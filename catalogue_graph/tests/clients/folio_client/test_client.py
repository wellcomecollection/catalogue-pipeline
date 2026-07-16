from collections.abc import Callable

import httpx

from clients.folio_client import FolioClient
from clients.folio_client.okapi_auth import OkapiAuth

BASE = "https://folio.example"


def _client_with(handler: Callable[[httpx.Request], httpx.Response]) -> FolioClient:
    """A FolioClient whose httpx.Client is pre-built onto a mock transport.

    Pre-setting ``_client`` skips only the lazy build in ``_http`` (credential
    resolution); the request path and OkapiAuth flow under test are unchanged.
    """
    fc = FolioClient(BASE, "t1", username="u", password="p")
    fc._client = httpx.Client(
        auth=OkapiAuth(base_url=BASE, tenant="t1", username="u", password="p"),
        transport=httpx.MockTransport(handler),
    )
    return fc


def test_request_logs_in_then_returns_status_and_json() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/authn/login":
            return httpx.Response(201, headers={"x-okapi-token": "tok"})
        assert request.headers["x-okapi-token"] == "tok"
        assert request.headers["x-okapi-tenant"] == "t1"
        return httpx.Response(200, json={"id": "x"})

    status, data = _client_with(handler).request("GET", "/inventory/items/x")

    assert status == 200
    assert data == {"id": "x"}


def test_request_reauthenticates_once_on_401() -> None:
    tokens = iter(["tok-1", "tok-2"])
    seen: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/authn/login":
            return httpx.Response(200, headers={"x-okapi-token": next(tokens)})
        seen.append(request.headers["x-okapi-token"])
        if len(seen) == 1:
            return httpx.Response(401)
        return httpx.Response(200, json={"ok": True})

    status, data = _client_with(handler).request("POST", "/inventory/items", {"a": 1})

    assert status == 200
    assert data == {"ok": True}
    # First attempt with the stale token, then one retry with the refreshed token.
    assert seen == ["tok-1", "tok-2"]


def test_request_empty_body_returns_empty_dict() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/authn/login":
            return httpx.Response(200, headers={"x-okapi-token": "tok"})
        return httpx.Response(204)  # OKAPI writes often return 204 no content

    status, data = _client_with(handler).request(
        "PUT", "/users/x", {"id": "x"}, accept="text/plain"
    )

    assert status == 204
    assert data == {}
