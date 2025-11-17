"""Factories for shared Axiell adapter clients (OAI + HTTPX)."""

from __future__ import annotations

from functools import cache

import httpx
from oai_pmh_client.client import OAIClient

from utils.aws import get_ssm_parameter

from adapters.axiell import config


def _http_timeout() -> httpx.Timeout:
    return httpx.Timeout(config.OAI_HTTP_TIMEOUT, read=config.OAI_MAX_READ_TIMEOUT)


@cache
def _oai_token() -> str:
    return get_ssm_parameter(config.SSM_OAI_TOKEN)


@cache
def _oai_endpoint() -> str:
    return get_ssm_parameter(config.SSM_OAI_URL)


class AuthenticatedHTTPXClient(httpx.Client):
    """HTTPX client that automatically appends the OAI token to each request."""

    def __init__(self, *, token: str | None = None, **kwargs) -> None:
        self._token = token or _oai_token()
        super().__init__(timeout=_http_timeout(), **kwargs)

    def build_request(self, method, url, **kwargs):  # type: ignore[override]
        params = kwargs.pop("params", {})
        params.setdefault("token", self._token)
        kwargs["params"] = params
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
        redacted_query_params=["token"],
    )
