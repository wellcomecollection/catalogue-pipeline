"""Factories for Axiell adapter HTTP clients."""

from __future__ import annotations

from functools import cache

import httpx
from httpx_ntlm import HttpNtlmAuth
from oai_pmh_client.client import OAIClient

from adapters.extractors.oai_pmh.axiell import config
from adapters.extractors.oai_pmh.http_client import OAIPMHHTTPClient
from utils.aws import get_ssm_parameter

# Axiell uses a custom "Token" header for authentication
AXIELL_AUTH_HEADER = "Token"


def _http_timeout() -> httpx.Timeout:
    return httpx.Timeout(config.OAI_HTTP_TIMEOUT, read=config.OAI_MAX_READ_TIMEOUT)


# Each takes the SSM parameter name, so that one process can hold clients for
# more than one Axiell system (live and UAT). The default is the live system's.
@cache
def _oai_token(param_name: str | None = None) -> str:
    return get_ssm_parameter(param_name or config.SSM_OAI_TOKEN)


@cache
def _oai_endpoint(param_name: str | None = None) -> str:
    return get_ssm_parameter(param_name or config.SSM_OAI_URL)


@cache
def _ntlm_auth(username_param: str, password_param: str) -> HttpNtlmAuth:
    return HttpNtlmAuth(
        get_ssm_parameter(username_param), get_ssm_parameter(password_param)
    )


def build_http_client(
    *,
    token: str | None = None,
    ssm_token_param: str | None = None,
    ssm_ntlm_username_param: str | None = None,
    ssm_ntlm_password_param: str | None = None,
) -> httpx.Client:
    """Build an HTTP client with Axiell authentication.

    The Token header is always sent. When both NTLM parameters are given, the
    client also does a Windows (NTLM) login: the UAT endpoint sits behind IIS
    Windows authentication and refuses a request with the token alone, while
    the live endpoint needs only the token.
    """
    auth = None
    if ssm_ntlm_username_param and ssm_ntlm_password_param:
        auth = _ntlm_auth(ssm_ntlm_username_param, ssm_ntlm_password_param)
    return OAIPMHHTTPClient(
        token=token or _oai_token(ssm_token_param),
        auth_header=AXIELL_AUTH_HEADER,
        timeout=_http_timeout(),
        auth=auth,
    )


def build_oai_client(
    *,
    http_client: httpx.Client | None = None,
    max_request_retries: int | None = None,
    ssm_url_param: str | None = None,
    ssm_token_param: str | None = None,
    ssm_ntlm_username_param: str | None = None,
    ssm_ntlm_password_param: str | None = None,
) -> OAIClient:
    """Build an OAI-PMH client for Axiell."""
    client = http_client or build_http_client(
        ssm_token_param=ssm_token_param,
        ssm_ntlm_username_param=ssm_ntlm_username_param,
        ssm_ntlm_password_param=ssm_ntlm_password_param,
    )
    return OAIClient(
        _oai_endpoint(ssm_url_param),
        client=client,
        max_request_retries=(
            config.OAI_MAX_RETRIES
            if max_request_retries is None
            else max_request_retries
        ),
        request_backoff_factor=config.OAI_BACKOFF_FACTOR,
        request_max_backoff=config.OAI_BACKOFF_MAX,
        max_transient_retries=config.OAI_TRANSIENT_RETRIES,
    )
