import gzip
import io
import os
import tempfile
from collections.abc import Generator
from typing import Any, TypedDict

from botocore.credentials import Credentials

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
    def open(cls, uri: str, mode: str, **kwargs: Any) -> Any:
        print(f"Opening {uri} in mode {mode}")
        if mode == "w":
            # Create an in-memory text stream and save the file object in the file lookup
            cls.file_lookup[uri] = io.StringIO()
        elif mode == "wb":
            # Create a temporary file, return a handle and save the location in the file lookup
            # We're ignoring "SIM115 Use a context manager for opening files" as we need to keep
            # the file around for the duration of the test, cleaning it up in reset_mocks.
            temp_file = tempfile.NamedTemporaryFile(delete=False)  # noqa: SIM115
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
    def get_secret_value(self, SecretId: str) -> dict:
        if SecretId == LOAD_BALANCER_SECRET_NAME:
            secret_value = MOCK_API_KEY
        elif SecretId == INSTANCE_ENDPOINT_SECRET_NAME:
            secret_value = MOCK_INSTANCE_ENDPOINT
        else:
            raise KeyError("Secret value does not exist.")

        return {"SecretString": secret_value}


class MockS3Client(MockAwsService):
    def __init__(self) -> None:
        return


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


class MockBoto3Session:
    def __init__(self) -> None:
        self.clients = {
            "secretsmanager": MockSecretsManagerClient(),
            "s3": MockS3Client(),
            "sns": MockSNSClient(),
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
        json_data: dict | None = None,
        content_bytes: bytes | None = None,
    ) -> None:
        MockRequest.responses.append(
            {
                "method": method,
                "url": url,
                "params": params,
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
        data: dict | None = None,
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
            ):
                return response["response"]

        raise Exception(f"Unexpected request: {method} {url} {params}")

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
    inputs: list[dict] = []

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
