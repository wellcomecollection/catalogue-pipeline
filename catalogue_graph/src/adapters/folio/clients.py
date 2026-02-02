"""Factories for FOLIO adapter HTTP clients."""

from __future__ import annotations

from functools import cache
from typing import Any

import httpx
from httpx import Request
from httpx._types import URLTypes

from adapters.folio import config
from utils.aws import get_ssm_parameter


def _http_timeout() -> httpx.Timeout:
    return httpx.Timeout(config.OAI_HTTP_TIMEOUT, read=config.OAI_MAX_READ_TIMEOUT)


@cache
def _oai_token() -> str:
    return get_ssm_parameter(config.SSM_OAI_TOKEN)


@cache
def _oai_endpoint() -> str:
    return get_ssm_parameter(config.SSM_OAI_URL)


class FolioHTTPXClient(httpx.Client):
    """
    HTTPX client that automatically injects the FOLIO API token.

    This client adds the Authorization header with the API token
    to every request made by this client.
    """

    def __init__(self, *, token: str | None = None, **kwargs: Any) -> None:
        self._token = token or _oai_token()
        super().__init__(timeout=_http_timeout(), **kwargs)

    def build_request(self, method: str, url: URLTypes, **kwargs: Any) -> Request:
        headers = kwargs.pop("headers", {})
        headers.setdefault("Authorization", self._token)
        kwargs["headers"] = headers
        return super().build_request(method, url, **kwargs)


def build_http_client() -> httpx.Client:
    return FolioHTTPXClient()
