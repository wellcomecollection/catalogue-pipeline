"""Tests for the FOLIO HTTP client."""

from __future__ import annotations

from unittest.mock import patch

import httpx

from adapters.folio.clients import FolioHTTPXClient, _http_timeout


def test_folio_client_adds_authorization_header() -> None:
    """Test that FolioHTTPXClient adds Authorization header to all requests."""
    with patch("adapters.folio.clients._oai_token", return_value="test-token"):
        client = FolioHTTPXClient()

    # Build a request and verify the header is present
    request = client.build_request("GET", "https://example.com/oai")
    assert request.headers.get("Authorization") == "test-token"


def test_folio_client_uses_explicit_token() -> None:
    """Test that explicit token parameter overrides SSM lookup."""
    client = FolioHTTPXClient(token="explicit-token")

    request = client.build_request("GET", "https://example.com/oai")
    assert request.headers.get("Authorization") == "explicit-token"


def test_folio_client_preserves_other_headers() -> None:
    """Test that FolioHTTPXClient preserves user-specified headers."""
    client = FolioHTTPXClient(token="test-token")

    request = client.build_request(
        "GET",
        "https://example.com/oai",
        headers={"Accept": "application/xml", "Custom-Header": "value"},
    )

    assert request.headers.get("Authorization") == "test-token"
    assert request.headers.get("Accept") == "application/xml"
    assert request.headers.get("Custom-Header") == "value"


def test_folio_client_does_not_override_explicit_authorization() -> None:
    """Test that explicit Authorization header is not overwritten."""
    client = FolioHTTPXClient(token="default-token")

    request = client.build_request(
        "GET",
        "https://example.com/oai",
        headers={"Authorization": "custom-auth"},
    )

    # setdefault should keep the existing value
    assert request.headers.get("Authorization") == "custom-auth"


def test_http_timeout_configuration() -> None:
    """Test that HTTP timeout is configured correctly."""
    timeout = _http_timeout()

    assert isinstance(timeout, httpx.Timeout)
    # Default values from config
    assert timeout.connect == 10.0
    assert timeout.read == 60.0


def test_folio_client_inherits_httpx_client() -> None:
    """Test that FolioHTTPXClient is a proper httpx.Client subclass."""
    client = FolioHTTPXClient(token="test-token")

    assert isinstance(client, httpx.Client)
    assert hasattr(client, "get")
    assert hasattr(client, "post")
