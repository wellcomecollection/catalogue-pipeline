from adapters.axiell.clients import AXIELL_AUTH_HEADER


def test_axiell_uses_token_header() -> None:
    assert AXIELL_AUTH_HEADER == "Token"
