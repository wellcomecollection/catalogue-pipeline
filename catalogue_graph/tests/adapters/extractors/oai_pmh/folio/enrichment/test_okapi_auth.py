import json

import httpx
import pytest

from adapters.extractors.oai_pmh.folio.enrichment.okapi_auth import (
    OkapiAuth,
    OkapiLoginError,
)

BASE = "https://inventory.example"


def _auth() -> OkapiAuth:
    return OkapiAuth(base_url=BASE, tenant="t1", username="u", password="p")


def test_logs_in_then_applies_token() -> None:
    seen: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request.url.path)
        if request.url.path == "/authn/login":
            assert request.headers["x-okapi-tenant"] == "t1"
            assert json.loads(request.content) == {"username": "u", "password": "p"}
            return httpx.Response(201, headers={"x-okapi-token": "tok-1"})
        assert request.headers["x-okapi-token"] == "tok-1"
        assert request.headers["x-okapi-tenant"] == "t1"
        return httpx.Response(200, json={"ok": True})

    client = httpx.Client(auth=_auth(), transport=httpx.MockTransport(handler))
    response = client.get(f"{BASE}/oai-pmh-view/enrichedInstances")

    assert response.status_code == 200
    assert seen == ["/authn/login", "/oai-pmh-view/enrichedInstances"]


def test_reauthenticates_once_on_401() -> None:
    tokens = iter(["tok-1", "tok-2"])
    data_calls: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/authn/login":
            return httpx.Response(200, headers={"x-okapi-token": next(tokens)})
        data_calls.append(request.headers["x-okapi-token"])
        if len(data_calls) == 1:
            return httpx.Response(401)
        return httpx.Response(200, json={"ok": True})

    client = httpx.Client(auth=_auth(), transport=httpx.MockTransport(handler))
    response = client.get(f"{BASE}/x")

    assert response.status_code == 200
    # First attempt with the stale token, then one retry with the refreshed token.
    assert data_calls == ["tok-1", "tok-2"]


def test_login_without_token_header_raises() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200)  # no x-okapi-token header

    client = httpx.Client(auth=_auth(), transport=httpx.MockTransport(handler))
    with pytest.raises(OkapiLoginError, match="no x-okapi-token"):
        client.get(f"{BASE}/x")


def test_login_failure_status_raises() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(422, json={"errors": []})

    client = httpx.Client(auth=_auth(), transport=httpx.MockTransport(handler))
    with pytest.raises(OkapiLoginError, match="login failed"):
        client.get(f"{BASE}/x")


def test_requires_all_credentials() -> None:
    with pytest.raises(ValueError, match="requires"):
        OkapiAuth(base_url=BASE, tenant="t1", username="", password="p")
