"""OKAPI login auth for FOLIO clients.

Handles login to OKAPI (POST ``/authn/login-with-expiry`` with username/password)
and re-authenticates on 401 errors since tokens are short-lived.

Extracts the token from the ``folioAccessToken`` cookie (or ``x-okapi-token`` header
as fallback) and replays it as the ``x-okapi-token`` request header.

Shared across FOLIO clients via :class:`httpx.Auth`.
"""

from __future__ import annotations

from collections.abc import Generator

import httpx


class OkapiLoginError(RuntimeError):
    """OKAPI login failed or returned no token."""


class OkapiAuth(httpx.Auth):
    """httpx auth that logs in to OKAPI and refreshes the token on a 401.

    Sends ``x-okapi-tenant`` on every request (including the login) and, once obtained,
    ``x-okapi-token``. Login is lazy (on the first request) and repeated at most once
    per request, when the server returns 401 (the token has expired).
    """

    def __init__(
        self, *, base_url: str, tenant: str, username: str, password: str
    ) -> None:
        if not (base_url and tenant and username and password):
            raise ValueError(
                "OKAPI auth requires base_url, tenant, username and password"
            )
        self._login_url = f"{base_url.rstrip('/')}/authn/login-with-expiry"
        self._tenant = tenant
        self._username = username
        self._password = password
        self._token: str | None = None

    def _login_request(self) -> httpx.Request:
        return httpx.Request(
            "POST",
            self._login_url,
            headers={"x-okapi-tenant": self._tenant, "accept": "application/json"},
            json={"username": self._username, "password": self._password},
        )

    def _store_token(self, response: httpx.Response) -> None:
        if response.status_code not in (200, 201):
            raise OkapiLoginError(f"OKAPI login failed ({response.status_code})")
        # /authn/login-with-expiry on Eureka/Keycloak (both prod and the dev sandbox)
        # delivers the token in the folioAccessToken cookie, not an x-okapi-token header.
        # Prefer the cookie, with a fallback to x-okapi-token for gateways that still return it.
        token = response.cookies.get("folioAccessToken") or response.headers.get("x-okapi-token")
        if not token:
            raise OkapiLoginError(
                "OKAPI login returned no x-okapi-token header or folioAccessToken cookie"
            )
        self._token = token

    def _apply(self, request: httpx.Request) -> None:
        request.headers["x-okapi-tenant"] = self._tenant
        request.headers["x-okapi-token"] = self._token or ""

    def auth_flow(
        self, request: httpx.Request
    ) -> Generator[httpx.Request, httpx.Response, None]:
        if self._token is None:
            self._store_token((yield self._login_request()))
        self._apply(request)
        response = yield request
        if response.status_code == 401:
            # Token has likely expired: re-login once and retry the request.
            self._store_token((yield self._login_request()))
            self._apply(request)
            yield request
