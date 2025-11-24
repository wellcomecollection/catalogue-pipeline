"""Factories for shared Axiell adapter clients (OAI + HTTPX)."""

from __future__ import annotations

from functools import cache
from typing import Any

import httpx
from httpx import Request
from httpx._types import URLTypes
from oai_pmh_client.client import OAIClient

from adapters.axiell import config
from utils.aws import get_ssm_parameter


def _http_timeout() -> httpx.Timeout:
    return httpx.Timeout(config.OAI_HTTP_TIMEOUT, read=config.OAI_MAX_READ_TIMEOUT)


@cache
def _oai_token() -> str:
    return get_ssm_parameter(config.SSM_OAI_TOKEN)


@cache
def _oai_endpoint() -> str:
    return get_ssm_parameter(config.SSM_OAI_URL)


class AuthenticatedHTTPXClient(httpx.Client):
    """
    HTTPX client that automatically injects the OAI token into all requests.

    This client ensures that the OAI token is added to the headers of every
    request made by this client.
    """

    def __init__(self, *, token: str | None = None, **kwargs: Any) -> None:
        self._token = token or _oai_token()
        super().__init__(timeout=_http_timeout(), **kwargs)

    def build_request(self, method: str, url: URLTypes, **kwargs: Any) -> Request:
        headers = kwargs.pop("headers", {})
        headers.setdefault("Token", self._token)
        kwargs["headers"] = headers
        return super().build_request(method, url, **kwargs)


def build_http_client() -> httpx.Client:
    return AuthenticatedHTTPXClient()


def build_oai_client(*, http_client: httpx.Client | None = None) -> OAIClient:
    client = http_client or build_http_client()
    return OAIClient(
        _oai_endpoint(),
        client=client,
        max_request_retries=config.OAI_MAX_RETRIES,
        request_backoff_factor=config.OAI_BACKOFF_FACTOR,
        request_max_backoff=config.OAI_BACKOFF_MAX,
    )
