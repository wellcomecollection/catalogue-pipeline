"""Tests for AdapterStoreSource, the Iceberg-backed source used by transformers."""

from collections.abc import Generator
from typing import cast

import pyarrow as pa

from adapters.transformers.adapter_store_source import AdapterStoreSource
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from tests.adapters.conftest import AdapterStoreFactory, adapter_records_to_table


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


class _StreamingOnlyStore:
    """Stub store that streams instrumented batches and forbids eager reads."""

    def __init__(self, batches: list[pa.RecordBatch]) -> None:
        self.batches = batches
        self.batches_consumed: list[int] = []

    def stream_active_namespace_records(
        self, snapshot_id: int | None = None
    ) -> Generator[pa.RecordBatch]:
        for index, batch in enumerate(self.batches):
            self.batches_consumed.append(index)
            yield batch

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
    assert store.batches_consumed == [0]

    assert next(stream)["id"] == "rec002"
    assert store.batches_consumed == [0, 1]


def test_stream_raw_full_reindex_empty_store() -> None:
    """A full reindex over an empty store yields nothing without erroring."""
    empty_batch = pa.RecordBatch.from_pylist([], schema=ADAPTER_STORE_ARROW_SCHEMA)
    store = _StreamingOnlyStore([empty_batch])
    source = AdapterStoreSource(cast(AdapterStore, store), changeset_ids=[])

    assert list(source.stream_raw()) == []
