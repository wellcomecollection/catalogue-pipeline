import datetime
import json
import typing

import backoff
import boto3
import requests
from botocore.auth import SigV4Auth
from botocore.awsrequest import AWSRequest

NEPTUNE_BACKOFF_DEFAULT_RETRIES = 3
NEPTUNE_BACKOFF_DEFAULT_INTERVAL = 10


def on_request_backoff(backoff_details: typing.Any) -> None:
    exception_name = type(backoff_details["exception"]).__name__
    print(f"Neptune request failed due to '{exception_name}'. Retrying...")


class BaseNeptuneClient:
    """
    Communicates with the Neptune cluster. Makes openCypher queries, triggers bulk load operations, etc.

    Do not use this base class directly. Use either LambdaNeptuneClient (when running in the same VPC as the Neptune
    cluster) or LocalNeptuneClient (when connecting to the cluster from outside the VPC).
    """

    def __init__(self, neptune_endpoint: str) -> None:
        self.session: boto3.Session | None = None
        self.neptune_endpoint: str = neptune_endpoint

    def _get_client_url(self) -> str:
        raise NotImplementedError()

    @backoff.on_exception(
        backoff.constant,
        Exception,
        max_tries=NEPTUNE_BACKOFF_DEFAULT_RETRIES,
        interval=NEPTUNE_BACKOFF_DEFAULT_INTERVAL,
        on_backoff=on_request_backoff,
    )
    def _make_request(
        self, method: str, relative_url: str, payload: dict | None = None
    ) -> dict:
        assert self.session
        credentials = self.session.get_credentials()
        assert credentials is not None

        client_url = self._get_client_url()

        url = f"{client_url}{relative_url}"
        headers = {"Host": self.neptune_endpoint, "Content-Type": "application/json"}
        data = json.dumps(payload or {})

        # We use IAM database authentication, which means we need to authenticate the request using AWS Signature
        request = AWSRequest(method=method, url=url, data=data, headers=headers)
        SigV4Auth(credentials, "neptune-db", "eu-west-1").add_auth(request)

        raw_response = requests.request(
            method, url, data=data, headers=dict(request.headers)
        )

        if raw_response.status_code != 200:
            raise Exception(raw_response.content)

        response: dict = raw_response.json()
        return response

    def run_open_cypher_query(self, query: str) -> dict:
        """Runs an openCypher query against the Neptune cluster. Automatically retries up to 5 times
        to mitigate transient errors."""
        payload = {"query": query}
        response = self._make_request("POST", "/openCypher", payload)
        results: dict = response["results"]
        return results

    def get_graph_summary(self) -> dict:
        """
        Returns a Neptune summary report about the graph.
        See https://docs.aws.amazon.com/neptune/latest/userguide/neptune-graph-summary.html for more info.
        """
        response = self._make_request("GET", "/propertygraph/statistics/summary")
        graph_summary: dict = response["payload"]["graphSummary"]
        return graph_summary

    def _reset_database(self) -> dict:
        """Irreversibly wipes all data from the database. This method only exists for development purposes."""
        # TODO: Only keep this function for testing purposes. Remove before releasing.
        data = {"action": "initiateDatabaseReset"}
        response = self._make_request("POST", "/system", data)
        reset_token = response["payload"]["token"]

        data = {"action": "performDatabaseReset", "token": reset_token}
        response = self._make_request("POST", "/system", data)

        return response

    def initiate_bulk_load(self, s3_file_uri: str) -> str:
        """
        Initiates a Neptune bulk load from an S3 file.
        See https://docs.aws.amazon.com/neptune/latest/userguide/load-api-reference-load.html for more info.
        """
        response = self._make_request(
            "POST",
            "/loader",
            {
                "source": s3_file_uri,
                "format": "opencypher",
                "iamRoleArn": "arn:aws:iam::760097843905:role/catalogue-graph-cluster",
                "region": "eu-west-1",
                "failOnError": "FALSE",
                "parallelism": "MEDIUM",
                "queueRequest": "TRUE",
                "userProvidedEdgeIds": "TRUE",
                "updateSingleCardinalityProperties": "TRUE",
            },
        )

        load_id: str = response["payload"]["loadId"]
        return load_id

    def get_bulk_load_status(self, load_id: str) -> str:
        """
        Checks the status of a Neptune bulk load job and prints the results. Returns the overall status of the job.
        See https://docs.aws.amazon.com/neptune/latest/userguide/load-api-reference-status-requests.html for more info.
        """
        # Response format: https://docs.aws.amazon.com/neptune/latest/userguide/load-api-reference-status-response.html
        response = self._make_request(
            "GET", f"/loader?loadId={load_id}&errors=TRUE&details=TRUE"
        )

        payload = response["payload"]
        overall_status = payload["overallStatus"]
        error_logs = payload["errors"]["errorLogs"]

        # Statuses: https://docs.aws.amazon.com/neptune/latest/userguide/loader-message.html
        status: str = overall_status["status"]
        processed_count = overall_status["totalRecords"]

        print(f"Bulk load status: {status}. (Processed {processed_count:,} records.)")

        if status in ("LOAD_NOT_STARTED", "LOAD_IN_QUEUE", "LOAD_IN_PROGRESS"):
            return status

        insert_error_count = overall_status["insertErrors"]
        parsing_error_count = overall_status["parsingErrors"]
        data_type_error_count = overall_status["datatypeMismatchErrors"]
        formatted_time = datetime.timedelta(seconds=overall_status["totalTimeSpent"])

        print(f"    Insert errors: {insert_error_count:,}")
        print(f"    Parsing errors: {parsing_error_count:,}")
        print(f"    Data type mismatch errors: {data_type_error_count:,}")
        print(f"    Total time spent: {formatted_time}")

        if error_logs:
            print("    First 10 errors:")

            for error_log in error_logs:
                code = error_log["errorCode"]
                message = error_log["errorMessage"]
                record_num = error_log["recordNum"]
                print(f"         {code}: {message}. (Row number: {record_num})")

        failed_feeds = payload.get("failedFeeds")
        if failed_feeds:
            print("    Failed feed statuses:")
            for failed_feed in failed_feeds:
                print(f"         {failed_feed['status']}")

        return status

    def get_bulk_load_statuses(self) -> list[str]:
        """Returns the loadIDs of the last 5 Neptune bulk load jobs."""
        response = self._make_request("GET", "/loader")
        payload: list[str] = response["payload"]
        return payload
