from __future__ import annotations

import datetime
import gzip
import io
import json
import os
import tempfile
from collections import defaultdict
from collections.abc import Generator
from typing import Any, TypedDict

import polars as pl
from botocore.credentials import Credentials
from polars import DataFrame as PolarsDataFrame

from utils.aws import (
    INSTANCE_ENDPOINT_SECRET_NAME,
    LOAD_BALANCER_SECRET_NAME,
)

MOCK_PUBLIC_ENDPOINT = "test-public-host.com"
MOCK_INSTANCE_ENDPOINT = "test-host.com"
MOCK_API_KEY = "TEST_SECRET_API_KEY_123"  # legacy constant retained
MOCK_CREDENTIALS = Credentials(
    access_key="test_access_key",
    secret_key="test",
    token="test_token",
)


class MockSmartOpen:
    file_lookup: dict = {}

    @classmethod
    def reset_mocks(cls) -> None:
        for file_path in cls.file_lookup.values():
            if isinstance(file_path, str) and os.path.exists(file_path):
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
    def mock_s3_parquet_file(cls, uri: str, content: PolarsDataFrame) -> None:
        if pl is None:  # pragma: no cover
            raise RuntimeError("polars not available for parquet mock")
        buffer = io.BytesIO()
        content.write_parquet(buffer)
        cls.file_lookup[uri] = buffer

    @classmethod
    def open(
        cls, uri: str, mode: str, encoding: str | None = None, **kwargs: Any
    ) -> Any:  # noqa: D401
        # Intentionally simple; emulate subset of smart_open.open
        print(f"Opening {uri} in mode {mode}")
        if mode in ("w", "wb"):
            # Respect text vs binary by passing encoding only for text
            open_kwargs: dict[str, Any] = {"mode": mode}
            if encoding and "b" not in mode:
                open_kwargs["encoding"] = encoding
            temp_file = tempfile.NamedTemporaryFile(delete=False, **open_kwargs)  # noqa: SIM115
            temp_file.to_boto3 = lambda _: MockBotoS3Object()  # type: ignore[attr-defined]
            cls.file_lookup[uri] = temp_file.name
            return temp_file
        if mode in ("r", "rb"):
            if uri not in cls.file_lookup:
                raise KeyError(f"Mock S3 file {uri} does not exist.")
            value = cls.file_lookup[uri]
            if isinstance(value, str):
                return open(value, mode)
            return value
        raise ValueError(f"Unsupported file mode: {mode}")


class MockAwsService:  # pragma: no cover - structural
    def __init__(self) -> None:  # noqa: D401
        return None


class MockSecretsManagerClient(MockAwsService):
    secrets: dict[str, Any] = {}

    @classmethod
    def add_mock_secret(cls, secret_id: str, value: Any) -> None:
        cls.secrets[secret_id] = value

    def get_secret_value(self, SecretId: str) -> dict:
        if SecretId == LOAD_BALANCER_SECRET_NAME:
            secret_value = MOCK_PUBLIC_ENDPOINT
        elif SecretId == INSTANCE_ENDPOINT_SECRET_NAME:
            secret_value = MOCK_INSTANCE_ENDPOINT
        elif SecretId in self.secrets:
            secret_value = self.secrets[SecretId]
        else:
            raise KeyError(f"Secret value '{SecretId}' does not exist.")
        return {"SecretString": secret_value}


class MockS3Client(MockAwsService):  # pragma: no cover - structural
    pass


class MockCloudwatchClient(MockAwsService):
    metrics_reported: list[dict] = []

    @staticmethod
    def reset_mocks() -> None:
        MockCloudwatchClient.metrics_reported = []

    def put_metric_data(self, Namespace: str, MetricData: list[dict]) -> None:  # noqa: N803
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

    def publish_batch(self, TopicArn: str, PublishBatchRequestEntries: list) -> Any:  # noqa: N803
        MockSNSClient.publish_batch_request_entries.append(
            {
                "TopicArn": TopicArn,
                "PublishBatchRequestEntries": PublishBatchRequestEntries,
            }
        )


class MockBoto3Resource:  # pragma: no cover - structural
    def __init__(self, resourceName: str) -> None:  # noqa: N803
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

    def get_credentials(self) -> Credentials:  # noqa: D401
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
        if content is not None:
            self.raw = io.BytesIO(gzip.compress(content))

    def json(self) -> dict | None:  # noqa: D401
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

    def __init__(
        self, config: dict, api_key: str, timeout: float | None = None
    ) -> None:  # noqa: D401
        pass

    @classmethod
    def bulk(
        cls,
        _: Any,
        operations: Generator[dict],
        *args: Any,
        raise_on_error: bool | None = None,  # noqa: ARG003 - parity only
        stats_only: bool | None = None,  # noqa: ARG003 - parity only
        **kwargs: Any,
    ) -> tuple[int, list]:
        # Accept elasticsearch.helpers.bulk extra parameters to avoid TypeError in tests
        for op in operations:
            cls.inputs.append(op)
        return (len(cls.inputs), [])  # emulate success_count, no errors

    @classmethod
    def reset_mocks(cls) -> None:
        cls.inputs = []
        cls.indexed_documents = defaultdict(dict[str, dict])
        cls.queries = []

    @classmethod
    def index(cls, index: str, id: str, document: dict) -> None:  # noqa: A003
        cls.indexed_documents[index][id] = {"_source": document, "_id": id}

    def delete_by_query(self, index: str, body: dict) -> dict:  # noqa: D401
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

    def open_point_in_time(self, index: str, keep_alive: str) -> dict:  # noqa: D401
        self.pit_index = index
        return {"id": "some_pit_id"}

    def close_point_in_time(self, body: dict) -> None:  # noqa: D401
        return None

    def _get_id_filter_from_query(self, query: dict) -> list[str] | None:
        ids: list[str] | None = None
        for item in query.get("bool", {}).get("must", []):
            if item.get("ids"):
                ids = item["ids"]["values"]
        if query.get("ids"):
            ids = query["ids"]["values"]
        return ids

    def search(self, body: dict) -> dict:  # noqa: D401
        self.queries.append(body["query"])
        search_after = body.get("search_after")
        all_documents = self.indexed_documents[self.pit_index].values()
        sorted_documents = sorted(all_documents, key=lambda d: d["_id"])
        filtered_ids = self._get_id_filter_from_query(body["query"])
        items = []
        for document in sorted_documents:
            item = {**document, "sort": document["_id"]}
            if filtered_ids is not None and document["_id"] not in filtered_ids:
                continue
            if search_after is None or item["sort"] > search_after:
                items.append(item)
        return {"hits": {"hits": items}}

    def mget(self, index: str, body: dict) -> dict:  # noqa: D401
        indexed_documents = self.indexed_documents[index]
        items = []
        for requested_id in body["ids"]:
            item = {"_id": requested_id}
            if requested_id in indexed_documents:
                item["found"] = True
                item["_source"] = indexed_documents[requested_id]["_source"]
            else:
                item["found"] = False
            items.append(item)
        return {"docs": items}


def fixed_datetime(year: int, month: int, day: int) -> type[datetime.datetime]:
    class FixedDateTime(datetime.datetime):
        @classmethod
        def now(cls, tz: datetime.tzinfo | None = None) -> FixedDateTime:  # noqa: D401
            return cls(year, month, day)

    return FixedDateTime


def mock_es_secrets(
    service_name: str, pipeline_date: str, is_public: bool = False
) -> None:
    host = "public" if is_public else "private"
    prefix = f"elasticsearch/pipeline_storage_{pipeline_date}"
    MockSecretsManagerClient.add_mock_secret(f"{prefix}/{host}_host", "test")
    MockSecretsManagerClient.add_mock_secret(f"{prefix}/port", 80)
    MockSecretsManagerClient.add_mock_secret(f"{prefix}/protocol", "http")
    MockSecretsManagerClient.add_mock_secret(f"{prefix}/{service_name}/api_key", "")


def add_neptune_mock_response(
    expected_query: str, expected_params: dict, mock_results: list[dict]
) -> None:
    query = " ".join(expected_query.split())
    MockRequest.mock_response(
        method="POST",
        url="https://test-host.com:8182/openCypher",
        json_data={"results": mock_results},
        body=json.dumps({"query": query, "parameters": expected_params}),
    )


def reset_all_mocks() -> None:
    MockRequest.reset_mocks()
    MockSmartOpen.reset_mocks()
    MockSNSClient.reset_mocks()
    MockElasticsearchClient.reset_mocks()
    MockCloudwatchClient.reset_mocks()
