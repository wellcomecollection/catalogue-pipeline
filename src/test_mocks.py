from botocore.credentials import Credentials

from utils.aws import INSTANCE_ENDPOINT_SECRET_NAME, LOAD_BALANCER_SECRET_NAME

MOCK_API_KEY = "TEST_SECRET_API_KEY_123"
MOCK_INSTANCE_ENDPOINT = "test-host.com"
MOCK_CREDENTIALS = Credentials(
    access_key="test_access_key",
    secret_key="test",
    token="test_token",
)


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


class MockBoto3Session:
    def __init__(self) -> None:
        self.clients = {
            "secretsmanager": MockSecretsManagerClient(),
        }

    def client(self, client_name: str) -> MockAwsService:
        if client_name not in self.clients:
            raise KeyError("There is no mock client for the specified client_name.")

        return self.clients[client_name]

    def get_credentials(self) -> Credentials:
        return MOCK_CREDENTIALS


class MockResponse:
    def __init__(
        self, status_code: int, json_data: dict = None, content: bytes = None
    ) -> None:
        self.json_data = json_data
        self.status_code = status_code
        self.content = content

    def json(self) -> dict | None:
        return self.json_data

    def content(self) -> bytes | None:
        return self.content


class MockRequest:
    responses: list[dict] = []
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
        status_code: int,
        json_data: dict = None,
        content: bytes = None,
    ) -> None:
        MockRequest.responses.append(
            {
                "method": method,
                "url": url,
                "response": MockResponse(status_code, json_data, content),
            }
        )

    @staticmethod
    def mock_responses(responses: list[dict]) -> None:
        for response in responses:
            MockRequest.mock_response(
                response["method"],
                response["url"],
                response["status_code"],
                response.get("json_data"),
                response.get("content"),
            )

    @staticmethod
    def request(method: str, url: str, data: dict, headers: dict) -> MockResponse:
        MockRequest.calls.append(
            {"method": method, "url": url, "data": data, "headers": headers}
        )
        for response in MockRequest.responses:
            if response["method"] == method and response["url"] == url:
                return response["response"]

        raise Exception(f"Unexpected request: {method} {url}")

    @staticmethod
    def get(url: str, data: dict = {}, headers: dict = {}) -> MockResponse:
        MockRequest.calls.append(
            {"method": "GET", "url": url, "data": data, "headers": headers}
        )
        for response in MockRequest.responses:
            if response["method"] == "GET" and response["url"] == url:
                return response["response"]

        raise Exception(f"Unexpected request: GET {url}")
