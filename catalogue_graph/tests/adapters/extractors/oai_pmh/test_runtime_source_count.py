"""Tests for the base OAIPMHRuntimeConfig.source_of_truth_count().

This is the generic path (raw ListIdentifiers + completeListSize) used by any
adapter that does not override it. Axiell overrides it with the WebAPI, so this
covers the fallback other adapters would use.
"""

from __future__ import annotations

from unittest.mock import MagicMock

import httpx
import pytest

from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig


class _StubConfig(OAIPMHRuntimeConfig):
    def __init__(self, client: httpx.Client) -> None:
        cfg = MagicMock()
        cfg.oai_metadata_prefix = "oai_dc"
        cfg.oai_set_spec = "collect"
        super().__init__(cfg)
        self._client = client

    def get_oai_endpoint(self) -> str:
        return "https://host.example/OAI/oai.ashx"

    def build_http_client(self) -> httpx.Client:
        return self._client


def _oai_list_identifiers(*, complete_list_size: int | None, headers: int) -> bytes:
    header_xml = "".join(
        f"<header><identifier>collect:{i}</identifier></header>" for i in range(headers)
    )
    token = (
        f'<resumptionToken completeListSize="{complete_list_size}">tok</resumptionToken>'
        if complete_list_size is not None
        else ""
    )
    return (
        '<?xml version="1.0"?>'
        '<OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">'
        f"<ListIdentifiers>{header_xml}{token}</ListIdentifiers>"
        "</OAI-PMH>"
    ).encode()


def _config(handler: object) -> _StubConfig:
    transport = httpx.MockTransport(handler)  # type: ignore[arg-type]
    return _StubConfig(httpx.Client(transport=transport))


def test_source_count_reads_complete_list_size() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.params["verb"] == "ListIdentifiers"
        assert request.url.params["set"] == "collect"
        return httpx.Response(
            200, content=_oai_list_identifiers(complete_list_size=54321, headers=100)
        )

    assert _config(handler).source_of_truth_count() == 54321


def test_source_count_single_page_without_token_counts_headers() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200, content=_oai_list_identifiers(complete_list_size=None, headers=7)
        )

    assert _config(handler).source_of_truth_count() == 7


def test_source_count_returns_none_on_empty_body() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b"")

    assert _config(handler).source_of_truth_count() is None


def test_base_enumerate_source_ids_is_none() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b"")

    assert _config(handler).enumerate_source_ids() is None


def test_source_count_raises_on_http_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, content=b"unavailable")

    with pytest.raises(httpx.HTTPStatusError):
        _config(handler).source_of_truth_count()
