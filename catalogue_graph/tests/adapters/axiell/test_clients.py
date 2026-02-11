"""Tests for the Axiell HTTP client."""

from adapters.axiell.clients import AXIELL_AUTH_HEADER


def test_axiell_uses_token_header() -> None:
    """Verify Axiell uses the custom 'Token' header for auth."""
    assert AXIELL_AUTH_HEADER == "Token"
