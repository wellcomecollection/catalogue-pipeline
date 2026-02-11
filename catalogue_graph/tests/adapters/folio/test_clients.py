"""Tests for the FOLIO HTTP client."""

from adapters.folio.clients import FOLIO_AUTH_HEADER


def test_folio_uses_authorization_header() -> None:
    """Verify FOLIO uses the standard 'Authorization' header for auth."""
    assert FOLIO_AUTH_HEADER == "Authorization"
