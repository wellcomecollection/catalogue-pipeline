from adapters.folio.clients import FOLIO_AUTH_HEADER


def test_folio_uses_authorization_header() -> None:
    assert FOLIO_AUTH_HEADER == "Authorization"
