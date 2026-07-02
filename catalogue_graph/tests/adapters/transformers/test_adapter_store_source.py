"""Tests for AdapterStoreSource, the Iceberg-backed source used by transformers."""

from collections.abc import Generator
from typing import Any, cast

import pyarrow as pa
import pytest

from adapters.transformers import adapter_store_source
from adapters.transformers.adapter_store_source import AdapterStoreSource
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from tests.adapters.conftest import AdapterStoreFactory, adapter_records_to_table


def _forbid(method_name: str) -> Any:
    def _raise(*args: Any, **kwargs: Any) -> None:
        raise AssertionError(f"{method_name} must not be called on this path")

    return _raise


def test_stream_raw_full_reindex_yields_all_active_rows(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """With no changeset ids, stream_raw yields every non-deleted row as a dict."""
    store = adapter_store_with_records(
        [
            {"id": "rec001", "content": "first"},
            {"id": "rec002", "content": "second", "deleted": False},
            {"id": "rec003", "content": "deleted", "deleted": True},
        ]
    )
    source = AdapterStoreSource(store, changeset_ids=[])

    rows = list(source.stream_raw())

    assert sorted(row["id"] for row in rows) == ["rec001", "rec002"]
    assert all(isinstance(row, dict) for row in rows)


def test_stream_raw_changeset_path_uses_changeset_lookup(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """With changeset ids, stream_raw yields only rows from those changesets."""
    store = adapter_store_with_records(
        [
            {"id": "rec001", "content": "first", "changeset": "changeset-1"},
            {"id": "rec002", "content": "second", "changeset": "changeset-2"},
            {"id": "rec003", "content": "third", "changeset": "changeset-3"},
        ]
    )
    source = AdapterStoreSource(store, changeset_ids=["changeset-1", "changeset-2"])

    rows = list(source.stream_raw())

    assert sorted(row["id"] for row in rows) == ["rec001", "rec002"]


def test_stream_raw_small_changeset_avoids_changeset_content_read(
    adapter_store_with_records: AdapterStoreFactory,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """A small changeset is read by record id, never via the un-prunable
    changeset-filtered content scan."""
    store = adapter_store_with_records(
        [
            {"id": "rec001", "content": "first", "changeset": "changeset-1"},
            {"id": "rec002", "content": "second", "changeset": "changeset-2"},
        ]
    )
    monkeypatch.setattr(
        store, "get_records_by_changeset", _forbid("get_records_by_changeset")
    )
    source = AdapterStoreSource(store, changeset_ids=["changeset-1"])

    rows = list(source.stream_raw())

    assert [row["id"] for row in rows] == ["rec001"]


def test_stream_raw_small_changeset_includes_deleted_rows_with_content(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """Deleted rows in a changeset are streamed with their preserved content,
    so downstream documents can be overwritten."""
    store = adapter_store_with_records(
        [
            {"id": "rec001", "content": "active", "changeset": "changeset-1"},
            {
                "id": "rec002",
                "content": "deleted with content preserved",
                "deleted": True,
                "changeset": "changeset-1",
            },
        ]
    )
    source = AdapterStoreSource(store, changeset_ids=["changeset-1"])

    rows = {row["id"]: row for row in source.stream_raw()}

    assert sorted(rows) == ["rec001", "rec002"]
    assert rows["rec002"]["deleted"] is True
    assert rows["rec002"]["content"] == "deleted with content preserved"


def test_stream_raw_falls_back_for_large_changesets(
    adapter_store_with_records: AdapterStoreFactory,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Above the threshold, stream_raw uses the eager changeset read instead of
    an id-filtered read."""
    store = adapter_store_with_records(
        [
            {"id": "rec001", "content": "first", "changeset": "changeset-1"},
            {"id": "rec002", "content": "second", "changeset": "changeset-1"},
        ]
    )
    monkeypatch.setattr(adapter_store_source, "SMALL_CHANGESET_THRESHOLD", 1)
    monkeypatch.setattr(store, "get_records_by_ids", _forbid("get_records_by_ids"))
    source = AdapterStoreSource(store, changeset_ids=["changeset-1"])

    rows = list(source.stream_raw())

    assert sorted(row["id"] for row in rows) == ["rec001", "rec002"]


def test_stream_raw_empty_changeset_yields_no_content_read(
    adapter_store_with_records: AdapterStoreFactory,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """A changeset matching no rows yields nothing and performs no content read."""
    store = adapter_store_with_records(
        [{"id": "rec001", "content": "first", "changeset": "changeset-1"}]
    )
    monkeypatch.setattr(store, "get_records_by_ids", _forbid("get_records_by_ids"))
    monkeypatch.setattr(
        store, "get_records_by_changeset", _forbid("get_records_by_changeset")
    )
    source = AdapterStoreSource(store, changeset_ids=["nonexistent"])

    assert list(source.stream_raw()) == []


class _ClosableBatchStream:
    """Iterable of record batches that records consumption and close(), like a reader."""

    def __init__(self, batches: list[pa.RecordBatch]) -> None:
        self.batches = batches
        self.batches_consumed: list[int] = []
        self.closed = False

    def __iter__(self) -> Generator[pa.RecordBatch]:
        for index, batch in enumerate(self.batches):
            self.batches_consumed.append(index)
            yield batch

    def close(self) -> None:
        self.closed = True


class _StreamingOnlyStore:
    """Stub store that streams instrumented batches and forbids eager reads."""

    def __init__(self, batches: list[pa.RecordBatch]) -> None:
        self.stream = _ClosableBatchStream(batches)

    def stream_active_namespace_records(
        self, snapshot_id: int | None = None
    ) -> _ClosableBatchStream:
        return self.stream

    def get_active_namespace_records(self, snapshot_id: int | None = None) -> pa.Table:
        raise AssertionError(
            "stream_raw must not materialise the namespace with an eager read"
        )


def _single_row_batch(record_id: str) -> pa.RecordBatch:
    table = adapter_records_to_table([{"id": record_id, "content": "content"}])
    return table.to_batches()[0]


def test_stream_raw_full_reindex_is_lazy() -> None:
    """stream_raw consumes record batches one at a time, never the whole table."""
    store = _StreamingOnlyStore(
        [_single_row_batch("rec001"), _single_row_batch("rec002")]
    )
    source = AdapterStoreSource(cast(AdapterStore, store), changeset_ids=[])

    stream = source.stream_raw()
    first_row = next(stream)

    assert first_row["id"] == "rec001"
    assert store.stream.batches_consumed == [0]

    assert next(stream)["id"] == "rec002"
    assert store.stream.batches_consumed == [0, 1]


def test_stream_raw_full_reindex_closes_reader_when_abandoned() -> None:
    """Abandoning the stream mid-iteration closes the underlying batch reader,
    so a consumer error does not leave the reader's prefetch reads running."""
    store = _StreamingOnlyStore(
        [_single_row_batch("rec001"), _single_row_batch("rec002")]
    )
    source = AdapterStoreSource(cast(AdapterStore, store), changeset_ids=[])

    stream = source.stream_raw()
    next(stream)
    stream.close()

    assert store.stream.closed


def test_stream_raw_full_reindex_empty_store() -> None:
    """A full reindex over an empty store yields nothing without erroring."""
    empty_batch = pa.RecordBatch.from_pylist([], schema=ADAPTER_STORE_ARROW_SCHEMA)
    store = _StreamingOnlyStore([empty_batch])
    source = AdapterStoreSource(cast(AdapterStore, store), changeset_ids=[])

    assert list(source.stream_raw()) == []
