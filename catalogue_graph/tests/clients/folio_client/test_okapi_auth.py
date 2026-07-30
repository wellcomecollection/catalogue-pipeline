import json

import httpx
import pytest

from clients.folio_client import (
    OkapiAuth,
    OkapiLoginError,
)

BASE = "https://inventory.example"


def _auth() -> OkapiAuth:
    return OkapiAuth(base_url=BASE, tenant="t1", username="u", password="p")


def _login_with_expiry_response(access_token: str) -> httpx.Response:
    """A realistic ``/authn/login-with-expiry`` response.

    Eureka/Keycloak (both prod and the dev sandbox) returns no x-okapi-token
    header; it sets the access token in the ``folioAccessToken`` cookie, plus a
    ``folioRefreshToken`` we do not use (we re-login on 401 instead).
    """
    return httpx.Response(
        201,
        headers=[
            ("set-cookie", f"folioAccessToken={access_token}; Path=/; HttpOnly"),
            ("set-cookie", "folioRefreshToken=refresh-tok; Path=/authn; HttpOnly"),
        ],
    )


def test_logs_in_via_cookie_then_applies_token() -> None:
    # The production path: log in at /authn/login-with-expiry, read the token
    # from the folioAccessToken cookie, replay it as the x-okapi-token header.
    seen: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request.url.path)
        if request.url.path == "/authn/login-with-expiry":
            assert request.headers["x-okapi-tenant"] == "t1"
            assert json.loads(request.content) == {"username": "u", "password": "p"}
            return _login_with_expiry_response("cookie-tok")
        assert request.headers["x-okapi-token"] == "cookie-tok"
        assert request.headers["x-okapi-tenant"] == "t1"
        return httpx.Response(200, json={"ok": True})

    client = httpx.Client(auth=_auth(), transport=httpx.MockTransport(handler))
    response = client.get(f"{BASE}/oai-pmh-view/enrichedInstances")

    assert response.status_code == 200
    assert seen == ["/authn/login-with-expiry", "/oai-pmh-view/enrichedInstances"]


def test_reauthenticates_once_on_401_via_cookie() -> None:
    # The short-lived cookie token expires mid-run: a 401 triggers one re-login,
    # and the retry must carry the freshly-issued cookie token.
    tokens = iter(["cookie-tok-1", "cookie-tok-2"])
    data_calls: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/authn/login-with-expiry":
            return _login_with_expiry_response(next(tokens))
        data_calls.append(request.headers["x-okapi-token"])
        if len(data_calls) == 1:
            return httpx.Response(401)
        return httpx.Response(200, json={"ok": True})

    client = httpx.Client(auth=_auth(), transport=httpx.MockTransport(handler))
    response = client.get(f"{BASE}/x")

    assert response.status_code == 200
    # First attempt with the stale token, then one retry with the refreshed token.
    assert data_calls == ["cookie-tok-1", "cookie-tok-2"]


def test_login_returning_x_okapi_token_header_only_raises() -> None:
    # /authn/login-with-expiry only ever sets the folioAccessToken cookie, so a
    # response carrying only an x-okapi-token header (and no cookie) is treated as
    # a missing token — we deliberately do not read the header.
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(201, headers={"x-okapi-token": "header-tok"})

    client = httpx.Client(auth=_auth(), transport=httpx.MockTransport(handler))
    with pytest.raises(OkapiLoginError, match="no folioAccessToken cookie"):
        client.get(f"{BASE}/x")


def test_login_without_token_cookie_raises() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200)  # no folioAccessToken cookie

    client = httpx.Client(auth=_auth(), transport=httpx.MockTransport(handler))
    with pytest.raises(OkapiLoginError, match="no folioAccessToken cookie"):
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
