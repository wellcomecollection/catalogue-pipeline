"""Factories for FOLIO adapter HTTP clients."""

from __future__ import annotations

from functools import cache

import httpx

from adapters.folio import config
from adapters.oai_pmh.http_client import OAIPMHHTTPClient
from utils.aws import get_ssm_parameter

# FOLIO uses the standard "Authorization" header
FOLIO_AUTH_HEADER = "Authorization"


def _http_timeout() -> httpx.Timeout:
    return httpx.Timeout(config.OAI_HTTP_TIMEOUT, read=config.OAI_MAX_READ_TIMEOUT)


@cache
def _oai_token() -> str:
    return get_ssm_parameter(config.SSM_OAI_TOKEN)


@cache
def _oai_endpoint() -> str:
    return get_ssm_parameter(config.SSM_OAI_URL)


def build_http_client(*, token: str | None = None) -> httpx.Client:
    """Build an HTTP client with FOLIO authentication."""
    return OAIPMHHTTPClient(
        token=token or _oai_token(),
        auth_header=FOLIO_AUTH_HEADER,
        timeout=_http_timeout(),
    )
