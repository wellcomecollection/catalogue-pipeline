"""Shared HTTP client for OAI-PMH adapters with configurable auth."""

from __future__ import annotations

from typing import Any

import httpx
from httpx import Request
from httpx._types import URLTypes


class OAIPMHHTTPClient(httpx.Client):
    """
    HTTPX client that injects an authentication token into requests.

    This client handles auth for OAI-PMH endpoints that require a token
    in a specific header. Different endpoints use different header names:

    - Axiell: "Token"
    - FOLIO: "Authorization"

    Args:
        token: The authentication token to include in requests.
        auth_header: The header name to use (default: "Authorization").
        timeout: Optional custom httpx.Timeout configuration.
        **kwargs: Additional arguments passed to httpx.Client.
    """

    def __init__(
        self,
        *,
        token: str,
        auth_header: str = "Authorization",
        timeout: httpx.Timeout | None = None,
        **kwargs: Any,
    ) -> None:
        self._token = token
        self._auth_header = auth_header
        super().__init__(timeout=timeout, **kwargs)

    def build_request(self, method: str, url: URLTypes, **kwargs: Any) -> Request:
        # Normalise headers to an httpx.Headers instance so we can safely mutate it,
        # regardless of whether the caller passed None, a mapping, or a sequence.
        headers = httpx.Headers(kwargs.pop("headers", None))
        if self._auth_header not in headers:
            headers[self._auth_header] = self._token
        kwargs["headers"] = headers
        return super().build_request(method, url, **kwargs)
