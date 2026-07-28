from unittest.mock import MagicMock

import pytest
from _pytest.monkeypatch import MonkeyPatch
from elastic_transport import ApiResponseMeta, HttpHeaders
from elasticsearch.exceptions import ApiError
from elasticsearch.exceptions import ConnectionError as ESConnectionError

from core.source import ElasticSource, _giveup_es_request


def _api_error(status: int) -> ApiError:
    meta = ApiResponseMeta(
        status=status,
        http_version="1.1",
        headers=HttpHeaders({}),
        duration=0.0,
        node=None,  # type: ignore[arg-type]
    )
    return ApiError("search_phase_execution_exception", meta=meta, body=None)


def _make_source() -> ElasticSource:
    # pit_id supplied so construction does not open a real PIT.
    return ElasticSource(
        es_client=MagicMock(),
        index_name="images-augmented-x",
        query={"match_all": {}},
        pit_id="pit-123",
    )


@pytest.fixture(autouse=True)
def _no_backoff_sleep(monkeypatch: MonkeyPatch) -> None:
    # Skip real backoff waits.
    monkeypatch.setattr("time.sleep", lambda *_a, **_k: None)


def test_search_retries_transient_503_then_succeeds() -> None:
    source = _make_source()
    source.es_client.search.side_effect = [  # type: ignore[attr-defined]
        _api_error(503),
        {"hits": {"hits": [{"_id": "1", "sort": [1]}]}},
    ]

    hits = source.search(slice_index=0)

    assert source.es_client.search.call_count == 2  # type: ignore[attr-defined]
    assert hits == [{"_id": "1", "sort": [1]}]


def test_search_retries_transient_connection_error() -> None:
    source = _make_source()
    source.es_client.search.side_effect = [  # type: ignore[attr-defined]
        ESConnectionError("dropped keep-alive"),
        {"hits": {"hits": []}},
    ]

    hits = source.search(slice_index=0)

    assert source.es_client.search.call_count == 2  # type: ignore[attr-defined]
    assert hits == []


def test_search_gives_up_immediately_on_non_retriable_status() -> None:
    source = _make_source()
    source.es_client.search.side_effect = _api_error(400)  # type: ignore[attr-defined]

    with pytest.raises(ApiError):
        source.search(slice_index=0)

    assert source.es_client.search.call_count == 1  # type: ignore[attr-defined]


@pytest.mark.parametrize(
    ("exc", "expected_giveup"),
    [
        (_api_error(503), False),
        (_api_error(429), False),
        (_api_error(502), False),
        (_api_error(504), False),
        (_api_error(400), True),
        (_api_error(404), True),
        (ESConnectionError("transport"), False),
    ],
)
def test_giveup_predicate(exc: Exception, expected_giveup: bool) -> None:
    assert _giveup_es_request(exc) is expected_giveup


def test_search_refreshes_pit_id_from_response() -> None:
    source = _make_source()
    source.es_client.search.return_value = {  # type: ignore[attr-defined]
        "hits": {"hits": []},
        "pit_id": "pit-456",
    }

    source.search(slice_index=0)

    assert source.pit_id == "pit-456"
