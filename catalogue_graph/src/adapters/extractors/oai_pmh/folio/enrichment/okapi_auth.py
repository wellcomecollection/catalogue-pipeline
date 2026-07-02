"""OKAPI login auth for the FOLIO inventory client.

mod-inventory-storage sits behind OKAPI, whose tokens are short-lived, so a static
token is not viable for a scheduled adapter. This performs the OKAPI login itself
(POST ``/authn/login`` with username/password, read the ``x-okapi-token`` response
header) and re-authenticates once on a 401. It mirrors the login flow of the shared
folio-client prototype, but on httpx so it composes with the existing streaming
inventory client (which the prototype's urllib/JSON transport cannot handle).

See https://github.com/wellcomecollection/catalogue-pipeline/pull/3438 for the design.
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
        self._login_url = f"{base_url.rstrip('/')}/authn/login"
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
        token = response.headers.get("x-okapi-token")
        if not token:
            raise OkapiLoginError("OKAPI login returned no x-okapi-token header")
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
