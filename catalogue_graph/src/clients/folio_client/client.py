"""Folio (OKAPI) JSON client.

Used by the Axiell → FOLIO sync for Inventory upserts. Authentication is the
shared service-account OKAPI login (:class:`~clients.folio_client.OkapiAuth`):
``POST /authn/login`` -> ``x-okapi-token``, sent as ``x-okapi-token`` /
``x-okapi-tenant`` on every request and refreshed once on a 401. The same auth
is used by the FOLIO inventory enrichment client, so the whole pipeline shares
one OKAPI auth implementation over ``httpx``.

A single ``FolioClient`` (and its underlying ``httpx.Client``) can be reused
across warm Lambda invocations.
"""

from __future__ import annotations

import json
import ssl
from collections.abc import Callable
from typing import Any

import httpx
import structlog

from .okapi_auth import OkapiAuth

logger = structlog.get_logger(__name__)

CredentialsProvider = Callable[[], "tuple[str, str]"]


class FolioError(RuntimeError):
    """A non-2xx Folio response (or a transport/login failure)."""

    def __init__(self, message: str, status: int | None = None, body: Any = None):
        super().__init__(message)
        self.status = status
        self.body = body


class FolioClient:
    def __init__(
        self,
        base_url: str,
        tenant: str,
        *,
        username: str | None = None,
        password: str | None = None,
        credentials_provider: CredentialsProvider | None = None,
        ssl_context: ssl.SSLContext | None = None,
        timeout: int = 30,
    ):
        if not base_url or not tenant:
            raise ValueError("base_url and tenant are required")
        self.base_url = base_url.rstrip("/")
        self.tenant = tenant
        self._username = username
        self._password = password
        self._credentials_provider = credentials_provider
        self._ssl_context = ssl_context
        self.timeout = timeout
        self._client: httpx.Client | None = None

    def _credentials(self) -> tuple[str, str]:
        if self._username is not None and self._password is not None:
            return self._username, self._password
        if self._credentials_provider is not None:
            self._username, self._password = self._credentials_provider()
            return self._username, self._password
        raise FolioError("No Folio credentials configured")

    def _http(self) -> httpx.Client:
        """Lazily build the shared ``httpx.Client``.

        ``OkapiAuth`` handles the OKAPI login, ``x-okapi-tenant``/``x-okapi-token``
        headers and the single 401 refresh. Credentials are resolved here (via the
        optional ``credentials_provider``) so a login failure surfaces on first use.
        """
        if self._client is None:
            username, password = self._credentials()
            self._client = httpx.Client(
                auth=OkapiAuth(
                    base_url=self.base_url,
                    tenant=self.tenant,
                    username=username,
                    password=password,
                ),
                verify=self._ssl_context if self._ssl_context is not None else True,
                timeout=self.timeout,
            )
        return self._client

    def request(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        *,
        accept: str = "application/json",
    ) -> tuple[int, Any]:
        """Authenticated OKAPI request (lazy login + one 401 re-auth via OkapiAuth)."""
        logger.info("okapi_request", method=method, path=path)
        kwargs: dict[str, Any] = {"headers": {"accept": accept}}
        if body is not None:
            kwargs["json"] = body
        response = self._http().request(method, f"{self.base_url}{path}", **kwargs)
        # Many OKAPI writes (e.g. PUT /users/{id}) return 204 with no body.
        text = response.text
        if not text:
            return response.status_code, {}
        try:
            return response.status_code, response.json()
        except json.JSONDecodeError:
            return response.status_code, text
