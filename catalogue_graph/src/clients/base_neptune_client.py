import json
import os
import threading
import time
import typing
from itertools import batched

import backoff
import boto3
import requests
from botocore.auth import SigV4Auth
from botocore.awsrequest import AWSRequest

NEPTUNE_REQUESTS_BACKOFF_RETRIES = int(os.environ.get("REQUESTS_BACKOFF_RETRIES", "3"))
NEPTUNE_REQUESTS_BACKOFF_INTERVAL = 10

DELETE_BATCH_SIZE = 10000

# Deleting by ID is more costly than deleting by label, so we need to use a smaller batch size to make it work.
ID_DELETE_BATCH_SIZE = 1000

ALLOW_DATABASE_RESET = False

NEPTUNE_MAX_PARALLEL_QUERIES = 10


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

        # Throttle the number of parallel requests to prevent overwhelming the cluster
        self.parallel_query_semaphore = threading.Semaphore(
            NEPTUNE_MAX_PARALLEL_QUERIES
        )

    def _get_client_url(self) -> str:
        raise NotImplementedError()

    @backoff.on_exception(
        backoff.constant,
        Exception,
        max_tries=NEPTUNE_REQUESTS_BACKOFF_RETRIES,
        interval=NEPTUNE_REQUESTS_BACKOFF_INTERVAL,
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

        with self.parallel_query_semaphore:
            raw_response = requests.request(
                method, url, data=data, headers=dict(request.headers)
            )

        if raw_response.status_code != 200:
            raise Exception(raw_response.content)

        response: dict = raw_response.json()
        return response

    def run_open_cypher_query(
        self, query: str, parameters: dict[str, typing.Any] | None = None
    ) -> list[dict]:
        """Runs an openCypher query against the Neptune cluster. Automatically retries up to 5 times
        to mitigate transient errors."""
        compact_query = " ".join(query.split())
        payload: dict = {"query": compact_query}
        if parameters is not None:
            payload["parameters"] = parameters

        response = self._make_request("POST", "/openCypher", payload)
        results: list[dict] = response["results"]
        return results

    def time_open_cypher_query(
        self, query: str, parameters: dict[str, typing.Any], query_label: str
    ) -> list[dict]:
        """Runs an openCypher query, measures its execution time, and prints a summary."""
        t = time.time()
        results = self.run_open_cypher_query(query, parameters)
        print(
            f"Ran '{query_label}' query in {round(time.time() - t)} seconds, retrieving {len(results)} records."
        )
        return results

    def get_graph_summary(self) -> dict:
        """
        Returns a Neptune summary report about the graph.
        See https://docs.aws.amazon.com/neptune/latest/userguide/neptune-graph-summary.html for more info.
        """
        response = self._make_request("GET", "/propertygraph/statistics/summary")
        graph_summary: dict = response["payload"]["graphSummary"]
        return graph_summary

    def _reset_database(self) -> dict | None:
        """Irreversibly wipes all data from the database. This method only exists for development purposes."""

        if ALLOW_DATABASE_RESET:
            data = {"action": "initiateDatabaseReset"}
            response = self._make_request("POST", "/system", data)
            reset_token = response["payload"]["token"]

            data = {"action": "performDatabaseReset", "token": reset_token}
            return self._make_request("POST", "/system", data)

        print("Cannot reset the database due to an active safety switch.")
        return None

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

    def get_bulk_load_status(self, load_id: str) -> dict:
        """
        Returns the status of a bulk load job.
        See https://docs.aws.amazon.com/neptune/latest/userguide/load-api-reference-status-requests.html for more info.
        """
        response = self._make_request(
            "GET", f"/loader?loadId={load_id}&errors=TRUE&details=TRUE"
        )

        payload: dict = response["payload"]
        return payload

    def get_bulk_load_statuses(self) -> list[str]:
        """Returns the loadIDs of the last 5 Neptune bulk load jobs."""
        response = self._make_request("GET", "/loader")
        payload: list[str] = response["payload"]
        return payload

    def count_nodes_wih_label(self, label: str) -> int:
        """Returns the number of nodes which have the specified label."""
        count_query = f"""
            MATCH (n:{label}) RETURN COUNT(*) AS count;
        """
        count: int = self.run_open_cypher_query(count_query)[0]["count"]
        return count

    def delete_all_nodes_with_label(self, label: str) -> None:
        """Removes all nodes with the specified label from the graph in batches."""
        while (c := self.count_nodes_wih_label(label)) > 0:
            print(f"Remaining nodes with label '{label}': {c}.")
            delete_query = f"""
                MATCH (n:{label}) WITH n ORDER BY n.id LIMIT {DELETE_BATCH_SIZE} DETACH DELETE n;
            """
            self.run_open_cypher_query(delete_query)

        print(f"Removed all nodes with label '{label}'.")

    def delete_nodes_by_id(self, ids: list[str]) -> None:
        """Removes all nodes with the specified ids from the graph."""
        delete_query = """
            MATCH (n) WHERE n.id IN $nodeIds DETACH DELETE n
        """

        previous_node_count = self.get_total_node_count()

        for batch in batched(ids, ID_DELETE_BATCH_SIZE, strict=False):
            self.run_open_cypher_query(delete_query, {"nodeIds": list(batch)})
            print(f"Deleted a batch of nodes. (Batch size: {ID_DELETE_BATCH_SIZE})")

        total_deleted = previous_node_count - self.get_total_node_count()
        print(f"Successfully deleted a total of {total_deleted} nodes from the graph.")

    def delete_edges_by_id(self, ids: list[str]) -> None:
        """Removes all edges with the specified ids from the graph."""
        delete_query = """
            MATCH ()-[e]-() WHERE id(e) IN $edgeIds DELETE e
        """

        previous_edge_count = self.get_total_edge_count()

        for batch in batched(ids, ID_DELETE_BATCH_SIZE, strict=False):
            self.run_open_cypher_query(delete_query, {"edgeIds": list(batch)})
            print(f"Deleted a batch of edges. (Batch size: {ID_DELETE_BATCH_SIZE})")

        total_deleted = previous_edge_count - self.get_total_edge_count()
        print(f"Successfully deleted a total of {total_deleted} edges from the graph.")

    def get_total_edge_count(self) -> int:
        query = """
            MATCH ()-[e]-() RETURN count(e) AS edgeCount
        """

        edge_count: int = self.run_open_cypher_query(query)[0]["edgeCount"]
        return edge_count

    def get_total_node_count(self) -> int:
        query = """
            MATCH (n) RETURN count(n) AS nodeCount
        """

        node_count: int = self.run_open_cypher_query(query)[0]["nodeCount"]
        return node_count
