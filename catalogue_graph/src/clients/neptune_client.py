import json
import os
import threading
import time
import typing
from collections.abc import Iterable, Iterator

import backoff
import boto3
import requests
import structlog
from botocore.auth import SigV4Auth
from botocore.awsrequest import AWSRequest

import config
from models.neptune_bulk_loader import BulkLoadStatusResponse
from utils.aws import get_secret
from utils.streaming import process_stream_in_parallel
from utils.types import EntityType

logger = structlog.get_logger(__name__)

NeptuneEnvironment = typing.Literal["prod", "dev"]

NEPTUNE_REQUESTS_BACKOFF_RETRIES = int(os.environ.get("REQUESTS_BACKOFF_RETRIES", "3"))
NEPTUNE_REQUESTS_BACKOFF_INTERVAL = 10
NEPTUNE_PORT = 8182

DELETE_BATCH_SIZE = 10000

# Deleting by ID is more costly than deleting by label, so we need to use a smaller batch size to make it work.
ID_DELETE_BATCH_SIZE = 1000

ALLOW_DATABASE_RESET = False

NEPTUNE_MAX_PARALLEL_QUERIES = 10
NEPTUNE_QUERY_THREAD_COUNT = 5


def on_request_backoff(backoff_details: typing.Any) -> None:
    exception_name = type(backoff_details["exception"]).__name__
    logger.warning("Neptune request failed, retrying", exception_name=exception_name)


class NeptuneClient:
    """
    Communicates with the Neptune cluster. Makes openCypher queries, triggers bulk load operations, etc.
    """

    def __init__(self, environment: NeptuneEnvironment = "prod") -> None:
        self.session = boto3.Session()

        if environment == "prod":
            endpoint_secret_name = config.NEPTUNE_PROD_HOST_SECRET_NAME
        else:
            endpoint_secret_name = config.NEPTUNE_DEV_HOST_SECRET_NAME

        logger.info("Creating Neptune client", environment=environment)

        self.neptune_endpoint: str = get_secret(endpoint_secret_name)

        # Throttle the number of parallel requests to prevent overwhelming the cluster
        self.parallel_query_semaphore = threading.Semaphore(
            NEPTUNE_MAX_PARALLEL_QUERIES
        )

    def _get_client_url(self) -> str:
        return f"https://{self.neptune_endpoint}:{NEPTUNE_PORT}"

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
                method, url, data=data, headers=request.headers
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
        logger.info(
            "Ran query",
            query_label=query_label,
            duration_seconds=round(time.time() - t),
            record_count=len(results),
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

        logger.warning("Cannot reset the database due to an active safety switch")
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

    def get_bulk_load_status(self, load_id: str) -> BulkLoadStatusResponse:
        """
        Returns the status of a bulk load job.
        See https://docs.aws.amazon.com/neptune/latest/userguide/load-api-reference-status-requests.html for more info.
        """
        response = self._make_request(
            "GET", f"/loader?loadId={load_id}&errors=TRUE&details=TRUE"
        )

        payload: dict = response["payload"]
        return BulkLoadStatusResponse.model_validate(payload)

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
            logger.info("Remaining nodes with label", label=label, count=c)
            delete_query = f"""
                MATCH (n:{label}) WITH n ORDER BY n.id LIMIT {DELETE_BATCH_SIZE} DETACH DELETE n;
            """
            self.run_open_cypher_query(delete_query)

        logger.info("Removed all nodes with label", label=label)

    def get_existing_ids(self, ids: list[str], entity_type: EntityType) -> list[str]:
        if entity_type == "nodes":
            query = "MATCH (n) WHERE id(n) IN $ids RETURN id(n) AS id"
        elif entity_type == "edges":
            query = "MATCH ()-[e]->() WHERE id(e) IN $ids RETURN id(e) AS id"
        else:
            raise ValueError(f"Unknown entity type: {entity_type}")

        return list(self.run_parallel_query(ids, query).keys())

    def delete_entities_by_id(self, ids: list[str], entity_type: EntityType) -> None:
        if entity_type == "nodes":
            query = "MATCH (n) WHERE id(n) IN $ids DETACH DELETE n"
        elif entity_type == "edges":
            query = "MATCH ()-[e]->() WHERE id(e) IN $ids DELETE e"
        else:
            raise ValueError(f"Unknown entity type: {entity_type}")

        self.run_parallel_query(ids, query, chunk_size=ID_DELETE_BATCH_SIZE)

    def delete_nodes_by_id(self, ids: list[str]) -> None:
        """Removes all nodes with the specified ids from the graph."""
        return self.delete_entities_by_id(ids, "nodes")

    def delete_edges_by_id(self, ids: list[str]) -> None:
        """Removes all edges with the specified ids from the graph."""
        return self.delete_entities_by_id(ids, "edges")

    def get_total_edge_count(self, label: str) -> int:
        query = f"MATCH ()-[e:{label}]->() RETURN count(e) AS count"
        edge_count: int = self.run_open_cypher_query(query)[0]["count"]
        return edge_count

    def get_total_node_count(self, label: str) -> int:
        query = f"MATCH (n: {label}) RETURN count(n) AS count"
        node_count: int = self.run_open_cypher_query(query)[0]["count"]
        return node_count

    def run_parallel_query(
        self,
        ids: Iterable[str],
        query: str,
        parameters: dict[str, typing.Any] | None = None,
        chunk_size: int = 2000,
    ) -> dict[str, dict]:
        """
        Split the specified ids into chunks and run the selected query against each chunk in parallel.
        Return a dictionary mapping each id to its corresponding result.
        """

        def _run_query(chunk: Iterable[str]) -> list[dict]:
            all_parameters = (parameters or {}) | {"ids": sorted(chunk)}
            return self.run_open_cypher_query(query, all_parameters)

        raw_results = process_stream_in_parallel(
            ids, _run_query, chunk_size, NEPTUNE_QUERY_THREAD_COUNT
        )

        return {item["id"]: item for item in raw_results}

    def get_disconnected_node_ids(
        self, node_label: str, edge_label: str
    ) -> Iterator[str]:
        """Return the IDs of all nodes which do not have any edges of the specified type."""
        query = f"""
            MATCH (n: {node_label})
            WHERE NOT (n)-[:{edge_label}]-()
            RETURN id(n) AS id
        """

        result = self.time_open_cypher_query(query, {}, "disconnected nodes")

        for item in result:
            yield item["id"]

    def get_node_edges(
        self, node_ids: Iterable[str], edge_label: str
    ) -> dict[str, set[str]]:
        """Return a dictionary mapping each ID to a set of edge IDs of the specified edge type."""
        query = f"""
            UNWIND $ids AS id
            MATCH (n {{`~id`: id}})-[e:{edge_label}]-()
            RETURN id(n) AS id, collect(id(e)) AS edge_ids
        """

        result = self.run_parallel_query(node_ids, query)
        return {node_id: set(item["edge_ids"]) for node_id, item in result.items()}
