from unittest.mock import MagicMock

import pytest
from elasticsearch.exceptions import TransportError

from core.source import ElasticIdsSource

QUERY = {"range": {"modifiedTime": {"gte": "2026-01-01T10:00:00Z"}}}


def _source(es_client: MagicMock, pit_id: str | None = None) -> ElasticIdsSource:
    return ElasticIdsSource(
        es_client=es_client,
        index_name="test-index",
        query=QUERY,
        pit_id=pit_id,
        fields=[],
        slice_count=2,
        parallelism=2,
    )


def _client_with_hits(ids: list[str]) -> MagicMock:
    client = MagicMock()
    client.count.return_value = {"count": len(ids)}
    client.open_point_in_time.return_value = {"id": "test-pit"}
    hits = [{"_id": _id, "sort": [i]} for i, _id in enumerate(ids)]

    def search(body: dict) -> dict:
        # Every hit on slice 0, and only on the first page
        if body.get("slice", {}).get("id", 0) == 0 and "search_after" not in body:
            return {"hits": {"hits": hits}}
        return {"hits": {"hits": []}}

    client.search.side_effect = search
    return client


def test_does_not_open_a_pit_on_construction() -> None:
    client = _client_with_hits([])

    _source(client)

    client.open_point_in_time.assert_not_called()


def test_empty_scope_is_one_count_and_no_pit_or_search() -> None:
    client = _client_with_hits([])

    assert list(_source(client).stream_ids()) == []

    client.count.assert_called_once_with(index="test-index", query=QUERY)
    client.open_point_in_time.assert_not_called()
    client.search.assert_not_called()


def test_streams_ids_then_closes_its_pit() -> None:
    client = _client_with_hits(["a", "b"])

    assert sorted(_source(client).stream_ids()) == ["a", "b"]

    client.open_point_in_time.assert_called_once()
    client.close_point_in_time.assert_called_once_with(body={"id": "test-pit"})


def test_leaves_a_pit_it_was_given_open() -> None:
    client = _client_with_hits(["a"])

    assert list(_source(client, pit_id="shared-pit").stream_ids()) == ["a"]

    client.open_point_in_time.assert_not_called()
    client.close_point_in_time.assert_not_called()


def test_closes_its_pit_when_a_search_fails() -> None:
    client = _client_with_hits(["a"])
    client.search.side_effect = ValueError("boom")

    with pytest.raises(ValueError):
        list(_source(client).stream_ids())

    client.close_point_in_time.assert_called_once()


def test_a_failed_close_does_not_fail_the_stream() -> None:
    client = _client_with_hits(["a"])
    client.close_point_in_time.side_effect = TransportError("closed")

    assert list(_source(client).stream_ids()) == ["a"]
