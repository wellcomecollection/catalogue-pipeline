import datetime
import gzip
import io
import os
import tempfile
from collections import defaultdict
from collections.abc import Generator
from typing import Any, TypedDict

import polars as pl
from botocore.credentials import Credentials

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorIndexerObject,
    IngestorLoaderLambdaEvent,
    IngestorTriggerLambdaEvent,
)
from utils.aws import INSTANCE_ENDPOINT_SECRET_NAME, LOAD_BALANCER_SECRET_NAME

MOCK_API_KEY = "TEST_SECRET_API_KEY_123"
MOCK_INSTANCE_ENDPOINT = "test-host.com"
MOCK_CREDENTIALS = Credentials(
    access_key="test_access_key",
    secret_key="test",
    token="test_token",
)


class MockSmartOpen:
    file_lookup: dict = {}

    @classmethod
    def reset_mocks(cls) -> None:
        # delete any temp files created
        for file_path in cls.file_lookup.values():
            if isinstance(file_path, str):
                os.remove(file_path)
        cls.file_lookup = {}

    @classmethod
    def mock_s3_file(cls, uri: str, content: str | bytes) -> None:
        if isinstance(content, str):
            cls.file_lookup[uri] = io.StringIO(content)
        elif isinstance(content, bytes):
            cls.file_lookup[uri] = io.BytesIO(content)
        else:
            raise ValueError("Unsupported content type!")

    @classmethod
    def mock_s3_parquet_file(cls, uri: str, content: pl.DataFrame) -> None:
        """Creates a parquet mock file from a polars dataframe."""
        buffer = io.BytesIO()
        content.write_parquet(buffer)
        cls.file_lookup[uri] = buffer

    @classmethod
    def open(cls, uri: str, mode: str, **kwargs: Any) -> Any:
        print(f"Opening {uri} in mode {mode}")
        if mode == "w" or mode == "wb":
            # Create a temporary file, return a handle and save the location in the file lookup
            # We're ignoring "SIM115 Use a context manager for opening files" as we need to keep
            # the file around for the duration of the test, cleaning it up in reset_mocks.
            temp_file = tempfile.NamedTemporaryFile(delete=False, mode=mode)  # noqa: SIM115
            # Insert the to_boto3 method to simulate the method provided by smart_open
            # https://github.com/piskvorky/smart_open/blob/develop/howto.md#how-to-access-s3-object-properties
            temp_file.to_boto3 = lambda _: MockBotoS3Object()  # type: ignore[attr-defined]

            cls.file_lookup[uri] = temp_file.name
            return temp_file
        elif mode in ["r", "rb"]:
            if uri not in cls.file_lookup:
                raise KeyError(f"Mock S3 file {uri} does not exist.")
            # if the file lookup is a str, then it's a file path and we should open it
            if isinstance(cls.file_lookup[uri], str):
                return open(cls.file_lookup[uri], mode)
        else:
            raise ValueError(f"Unsupported file mode: {mode}")

        return cls.file_lookup[uri]


class MockAwsService:
    def __init__(self) -> None:
        return None


class MockSecretsManagerClient(MockAwsService):
    secrets: dict[str, Any] = {}

    @classmethod
    def add_mock_secret(cls, secret_id: str, value: Any) -> None:
        cls.secrets[secret_id] = value

    def get_secret_value(self, SecretId: str) -> dict:
        if SecretId == LOAD_BALANCER_SECRET_NAME:
            secret_value = MOCK_API_KEY
        elif SecretId == INSTANCE_ENDPOINT_SECRET_NAME:
            secret_value = MOCK_INSTANCE_ENDPOINT
        elif SecretId in self.secrets:
            secret_value = self.secrets[SecretId]
        else:
            raise KeyError(f"Secret value '{SecretId}' does not exist.")

        return {"SecretString": secret_value}


class MockS3Client(MockAwsService):
    def __init__(self) -> None:
        return


class MockCloudwatchClient(MockAwsService):
    metrics_reported: list[dict] = []

    def __init__(self) -> None:
        return

    @staticmethod
    def reset_mocks() -> None:
        MockCloudwatchClient.metrics_reported = []

    def put_metric_data(
        self,
        Namespace: str,
        MetricData: list[dict],
    ) -> None:
        for metric in MetricData:
            dimensions = {d["Name"]: d["Value"] for d in metric["Dimensions"]}
            self.metrics_reported.append(
                {
                    "namespace": Namespace,
                    "metric_name": metric["MetricName"],
                    "value": metric["Value"],
                    "dimensions": dimensions,
                }
            )


class MockSNSClient(MockAwsService):
    publish_batch_request_entries: list[dict] = []

    @staticmethod
    def reset_mocks() -> None:
        MockSNSClient.publish_batch_request_entries = []

    def __init__(self) -> None:
        return

    def publish_batch(self, TopicArn: str, PublishBatchRequestEntries: list) -> Any:
        MockSNSClient.publish_batch_request_entries.append(
            {
                "TopicArn": TopicArn,
                "PublishBatchRequestEntries": PublishBatchRequestEntries,
            }
        )


class MockBoto3Resource:
    def __init__(self, resourceName: str) -> None:
        return None


class MockBotoS3Object:
    def __init__(self) -> None:
        self.content_length = 1


class MockBoto3Session:
    def __init__(self) -> None:
        self.clients = {
            "secretsmanager": MockSecretsManagerClient(),
            "s3": MockS3Client(),
            "sns": MockSNSClient(),
            "cloudwatch": MockCloudwatchClient(),
        }

    def client(self, client_name: str) -> MockAwsService:
        if client_name not in self.clients:
            raise KeyError("There is no mock client for the specified client_name.")

        return self.clients[client_name]

    def get_credentials(self) -> Credentials:
        return MOCK_CREDENTIALS


class MockResponse:
    def __init__(
        self,
        status_code: int | None = None,
        json_data: dict | None = None,
        content: bytes | None = None,
    ) -> None:
        self.json_data = json_data
        self.status_code = status_code
        self.content = content
        self.raw: Any = None

        # Assume raw content is gzipped
        if content is not None:
            self.raw = io.BytesIO(gzip.compress(content))
        else:
            self.raw = None

    def json(self) -> dict | None:
        return self.json_data


class MockRequestExpectation(TypedDict):
    method: str
    url: str
    params: dict | None
    response: MockResponse
    data: dict | str | None


class MockResponseInput(TypedDict):
    method: str
    url: str
    status_code: int
    params: dict | None
    content_bytes: bytes | None
    json_data: dict | None


class MockRequest:
    responses: list[MockRequestExpectation] = []
    calls: list[dict] = []

    @staticmethod
    def clear_mock_responses() -> None:
        MockRequest.responses = []

    @staticmethod
    def clear_mock_calls() -> None:
        MockRequest.calls = []

    @staticmethod
    def reset_mocks() -> None:
        MockRequest.clear_mock_responses()
        MockRequest.clear_mock_calls()

    @staticmethod
    def mock_response(
        method: str,
        url: str,
        status_code: int = 200,
        params: dict | None = None,
        body: dict | str | None = None,
        json_data: dict | None = None,
        content_bytes: bytes | None = None,
    ) -> None:
        MockRequest.responses.append(
            {
                "method": method,
                "url": url,
                "params": params,
                "data": body,
                "response": MockResponse(status_code, json_data, content_bytes),
            }
        )

    @staticmethod
    def mock_responses(responses: list[MockResponseInput]) -> None:
        for response in responses:
            MockRequest.mock_response(**response)

    @staticmethod
    def request(
        method: str,
        url: str,
        stream: bool = False,
        data: dict | str | None = None,
        headers: dict | None = None,
        params: dict | None = None,
    ) -> MockResponse:
        MockRequest.calls.append(
            {"method": method, "url": url, "data": data, "headers": headers}
        )

        for response in MockRequest.responses:
            if (
                response["method"] == method
                and response["url"] == url
                and response["params"] == params
                # If the expected response also specifies the body of the request, make sure it matches
                # the actual response. If not, ignore it.
                and (response.get("data") is None or response["data"] == data)
            ):
                return response["response"]

        raise Exception(f"Unexpected request: {method} {url} {params} {data}")

    @staticmethod
    def get(
        url: str,
        stream: bool = False,
        data: dict | None = None,
        headers: dict | None = None,
        params: dict | None = None,
    ) -> MockResponse:
        return MockRequest.request("GET", url, stream, data, headers, params)


class MockBulkResponse:
    def __init__(self, data: Any) -> None:
        self.body = {"items": data}


class MockElasticsearchClient:
    indexed_documents: dict = defaultdict(dict[str, dict])
    inputs: list[dict] = []
    pit_index: str
    queries: list[dict] = []

    def __init__(self, config: dict, api_key: str) -> None:
        pass

    @classmethod
    def bulk(cls, _: Any, operations: Generator[dict]) -> tuple[int, None]:
        for op in operations:
            cls.inputs.append(op)
        return (len(cls.inputs), None)

    @classmethod
    def reset_mocks(cls) -> None:
        cls.inputs = []
        cls.indexed_documents = defaultdict(dict[str, dict])
        cls.queries = []

    @classmethod
    def index(cls, index: str, id: str, document: dict) -> None:
        cls.indexed_documents[index][id] = {"_source": document, "_id": id}

    def delete_by_query(self, index: str, body: dict) -> dict:
        deleted_ids = body["query"]["ids"]["values"]

        new_indexed_documents = {}

        deleted_count = 0
        for _id, document in self.indexed_documents[index].items():
            if _id not in deleted_ids:
                new_indexed_documents[_id] = document
            else:
                deleted_count += 1

        self.indexed_documents[index] = new_indexed_documents

        return {"deleted": deleted_count}

    def count(self, index: str) -> dict:
        return {"count": len(self.indexed_documents.get(index, {}).values())}

    def open_point_in_time(self, index: str, keep_alive: str) -> dict:
        self.pit_index = index
        return {"id": "some_pit_id"}

    def close_point_in_time(self, body: dict) -> None:
        pass

    def search(self, body: dict) -> dict:
        self.queries.append(body["query"])
        search_after = body.get("search_after")

        all_documents = self.indexed_documents[self.pit_index].values()
        sorted_documents = sorted(all_documents, key=lambda d: d["_id"])

        items = []
        for document in sorted_documents:
            item = dict(document)
            item["sort"] = item["_id"]
            if search_after is None or item["sort"] > search_after:
                items.append(item)

        return {"hits": {"hits": items}}


def fixed_datetime(year: int, month: int, day: int) -> type[datetime.datetime]:
    class FixedDateTime(datetime.datetime):
        @classmethod
        def now(cls, tz: datetime.tzinfo | None = None) -> "FixedDateTime":
            return cls(year, month, day)

    return FixedDateTime


def get_mock_ingestor_trigger_event(job_id: str) -> IngestorTriggerLambdaEvent:
    return IngestorTriggerLambdaEvent(
        ingestor_type="concepts",
        pipeline_date="2025-01-01",
        index_date="2025-03-01",
        job_id=job_id,
    )


def get_mock_ingestor_loader_event(
    job_id: str, start_offset: int, end_index: int
) -> IngestorLoaderLambdaEvent:
    return IngestorLoaderLambdaEvent(
        **dict(get_mock_ingestor_trigger_event(job_id)),
        start_offset=start_offset,
        end_index=end_index,
    )


def get_mock_ingestor_indexer_event(job_id: str) -> IngestorIndexerLambdaEvent:
    return IngestorIndexerLambdaEvent(
        **dict(get_mock_ingestor_trigger_event(job_id)),
        object_to_index=IngestorIndexerObject(
            s3_uri=f"s3://test-bucket/test-prefix_concepts/2025-01-01/2025-03-01/{job_id}/00000000-00000001.parquet",
            content_length=1,
            record_count=1,
        ),
    )


def mock_es_secrets(
    service_name: str, pipeline_date: str, is_local: bool = False
) -> None:
    """Mock AWS Secrets Manager secrets to simulate connecting to the production cluster."""
    host = "public" if is_local else "private"
    prefix = f"elasticsearch/pipeline_storage_{pipeline_date}"
    MockSecretsManagerClient.add_mock_secret(f"{prefix}/{host}_host", "test")
    MockSecretsManagerClient.add_mock_secret(f"{prefix}/port", 80)
    MockSecretsManagerClient.add_mock_secret(f"{prefix}/protocol", "http")
    MockSecretsManagerClient.add_mock_secret(f"{prefix}/{service_name}/api_key", "")
