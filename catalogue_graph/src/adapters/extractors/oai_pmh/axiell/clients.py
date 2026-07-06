"""Factories for Axiell adapter HTTP clients."""

from __future__ import annotations

from functools import cache

import httpx
from oai_pmh_client.client import OAIClient

from adapters.extractors.oai_pmh.axiell import config
from adapters.extractors.oai_pmh.http_client import OAIPMHHTTPClient
from adapters.extractors.oai_pmh.resilient_client import ResilientOAIClient
from utils.aws import get_ssm_parameter

# Axiell uses a custom "Token" header for authentication
AXIELL_AUTH_HEADER = "Token"


def _http_timeout() -> httpx.Timeout:
    return httpx.Timeout(config.OAI_HTTP_TIMEOUT, read=config.OAI_MAX_READ_TIMEOUT)


@cache
def _oai_token() -> str:
    return get_ssm_parameter(config.SSM_OAI_TOKEN)


@cache
def _oai_endpoint() -> str:
    return get_ssm_parameter(config.SSM_OAI_URL)


def build_http_client(*, token: str | None = None) -> httpx.Client:
    """Build an HTTP client with Axiell authentication."""
    return OAIPMHHTTPClient(
        token=token or _oai_token(),
        auth_header=AXIELL_AUTH_HEADER,
        timeout=_http_timeout(),
    )


def build_oai_client(*, http_client: httpx.Client | None = None) -> OAIClient:
    """Build an OAI-PMH client for Axiell."""
    client = http_client or build_http_client()
    return ResilientOAIClient(
        _oai_endpoint(),
        client=client,
        empty_body_retries=config.OAI_EMPTY_BODY_RETRIES,
        empty_body_backoff_factor=config.OAI_BACKOFF_FACTOR,
        empty_body_backoff_max=config.OAI_BACKOFF_MAX,
        max_request_retries=config.OAI_MAX_RETRIES,
        request_backoff_factor=config.OAI_BACKOFF_FACTOR,
        request_max_backoff=config.OAI_BACKOFF_MAX,
    )
