"""Tests for ResilientOAIClient retry behaviour.

Uses httpx.MockTransport to fake the OAI-PMH server, covering the empty-body
(HTTP 200, no content) failure mode and transport/5xx errors on both first
pages and resumption-token pages.
"""

from __future__ import annotations

from collections.abc import Callable

import httpx
import pytest
from lxml import etree

from adapters.extractors.oai_pmh.resilient_client import ResilientOAIClient

BASE_URL = "http://oai.test/oai"

FIRST_PAGE_XML = """<?xml version="1.0" encoding="UTF-8"?>
<OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
  <responseDate>2025-01-01T00:00:00Z</responseDate>
  <request verb="ListRecords">http://oai.test/oai</request>
  <ListRecords>
    <record>
      <header>
        <identifier>id:1</identifier>
        <datestamp>2025-01-01T00:00:00Z</datestamp>
      </header>
      <metadata><payload>one</payload></metadata>
    </record>
    <resumptionToken>token-1</resumptionToken>
  </ListRecords>
</OAI-PMH>
"""

TOKEN_PAGE_XML = """<?xml version="1.0" encoding="UTF-8"?>
<OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
  <responseDate>2025-01-01T00:00:00Z</responseDate>
  <request verb="ListRecords">http://oai.test/oai</request>
  <ListRecords>
    <record>
      <header>
        <identifier>id:2</identifier>
        <datestamp>2025-01-01T00:05:00Z</datestamp>
      </header>
      <metadata><payload>two</payload></metadata>
    </record>
  </ListRecords>
</OAI-PMH>
"""

SINGLE_PAGE_XML = TOKEN_PAGE_XML


class RequestLog:
    """Records requests seen by the mock transport."""

    def __init__(self) -> None:
        self.requests: list[httpx.Request] = []

    def append(self, request: httpx.Request) -> None:
        self.requests.append(request)

    @property
    def count(self) -> int:
        return len(self.requests)

    def token_request_count(self, token: str) -> int:
        return sum(
            1
            for request in self.requests
            if request.url.params.get("resumptionToken") == token
        )


def _build_client(
    handler: Callable[[httpx.Request], httpx.Response],
    *,
    empty_body_retries: int = 3,
) -> ResilientOAIClient:
    return ResilientOAIClient(
        BASE_URL,
        client=httpx.Client(transport=httpx.MockTransport(handler)),
        empty_body_retries=empty_body_retries,
        empty_body_backoff_factor=0.0,
        empty_body_backoff_max=0.0,
    )


def test_empty_first_page_is_retried_then_succeeds() -> None:
    log = RequestLog()

    def handler(request: httpx.Request) -> httpx.Response:
        log.append(request)
        if log.count <= 2:
            return httpx.Response(200, content=b"")
        return httpx.Response(200, content=SINGLE_PAGE_XML.encode())

    client = _build_client(handler)
    records = list(client.list_records(metadata_prefix="oai_marcxml"))

    assert [r.header.identifier for r in records if r.header] == ["id:2"]
    assert log.count == 3


def test_empty_token_page_raises_immediately() -> None:
    log = RequestLog()

    def handler(request: httpx.Request) -> httpx.Response:
        log.append(request)
        if "resumptionToken" in request.url.params:
            return httpx.Response(200, content=b"")
        return httpx.Response(200, content=FIRST_PAGE_XML.encode())

    client = _build_client(handler)
    with pytest.raises(etree.XMLSyntaxError):
        list(client.list_records(metadata_prefix="oai_marcxml"))

    # The burned token must not be replayed: exactly one token-page request.
    assert log.token_request_count("token-1") == 1


def test_transport_error_on_first_page_is_retried() -> None:
    log = RequestLog()

    def handler(request: httpx.Request) -> httpx.Response:
        log.append(request)
        if log.count == 1:
            raise httpx.ConnectError("connection refused", request=request)
        return httpx.Response(200, content=SINGLE_PAGE_XML.encode())

    client = _build_client(handler)
    records = list(client.list_records(metadata_prefix="oai_marcxml"))

    assert [r.header.identifier for r in records if r.header] == ["id:2"]
    assert log.count == 2


def test_transport_error_on_token_page_is_retried_once_then_raises() -> None:
    log = RequestLog()

    def handler(request: httpx.Request) -> httpx.Response:
        log.append(request)
        if "resumptionToken" in request.url.params:
            raise httpx.ConnectError("connection refused", request=request)
        return httpx.Response(200, content=FIRST_PAGE_XML.encode())

    client = _build_client(handler)
    with pytest.raises(httpx.ConnectError):
        list(client.list_records(metadata_prefix="oai_marcxml"))

    # One initial attempt plus exactly one retry.
    assert log.token_request_count("token-1") == 2


def test_transport_error_on_token_page_can_recover_on_retry() -> None:
    log = RequestLog()

    def handler(request: httpx.Request) -> httpx.Response:
        log.append(request)
        if "resumptionToken" in request.url.params:
            if log.token_request_count("token-1") == 1:
                raise httpx.ConnectError("connection refused", request=request)
            return httpx.Response(200, content=TOKEN_PAGE_XML.encode())
        return httpx.Response(200, content=FIRST_PAGE_XML.encode())

    client = _build_client(handler)
    records = list(client.list_records(metadata_prefix="oai_marcxml"))

    assert [r.header.identifier for r in records if r.header] == ["id:1", "id:2"]


def test_empty_body_retries_exhausted_raises() -> None:
    log = RequestLog()

    def handler(request: httpx.Request) -> httpx.Response:
        log.append(request)
        return httpx.Response(200, content=b"")

    client = _build_client(handler, empty_body_retries=2)
    with pytest.raises(etree.XMLSyntaxError):
        list(client.list_records(metadata_prefix="oai_marcxml"))

    # One initial attempt plus two retries.
    assert log.count == 3


def test_5xx_response_is_retried() -> None:
    log = RequestLog()

    def handler(request: httpx.Request) -> httpx.Response:
        log.append(request)
        if log.count == 1:
            return httpx.Response(503, content=b"unavailable")
        return httpx.Response(200, content=SINGLE_PAGE_XML.encode())

    client = _build_client(handler)
    records = list(client.list_records(metadata_prefix="oai_marcxml"))

    assert [r.header.identifier for r in records if r.header] == ["id:2"]
    assert log.count == 2


def test_4xx_response_is_not_retried() -> None:
    log = RequestLog()

    def handler(request: httpx.Request) -> httpx.Response:
        log.append(request)
        return httpx.Response(404, content=b"not found")

    client = _build_client(handler)
    with pytest.raises(httpx.HTTPStatusError):
        list(client.list_records(metadata_prefix="oai_marcxml"))

    assert log.count == 1
