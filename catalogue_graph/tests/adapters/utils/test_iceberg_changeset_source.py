"""Tests for IcebergChangesetSource, the transformer planner's listing of changesets."""

from pyiceberg.table import Table as IcebergTable

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg_changeset_source import IcebergChangesetSource
from tests.adapters.conftest import AdapterStoreFactory, adapter_records_to_table


def _store(adapter_store_with_records: AdapterStoreFactory) -> AdapterStore:
    return adapter_store_with_records(
        [
            {"id": "rec001", "content": "a", "changeset": "changeset-2"},
            {"id": "rec002", "content": "b", "changeset": "changeset-2"},
            {
                "id": "rec003",
                "content": "c",
                "changeset": "changeset-2",
                "deleted": True,
            },
            {"id": "rec004", "content": "d", "changeset": "changeset-1"},
        ]
    )


def test_changeset_scope_counts_rows_and_tombstones_in_request_order(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    source = IcebergChangesetSource(
        _store(adapter_store_with_records),
        changeset_ids=["changeset-2", "changeset-1"],
    )

    assert source.changesets == [("changeset-2", 3), ("changeset-1", 1)]
    assert list(source.stream_ids()) == ["changeset-2", "changeset-1"]
    assert source.row_count == 4
    assert source.tombstone_count == 1


def test_a_requested_changeset_with_no_rows_is_still_listed(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    source = IcebergChangesetSource(
        _store(adapter_store_with_records),
        changeset_ids=["changeset-1", "changeset-gone"],
    )

    assert source.changesets == [("changeset-1", 1), ("changeset-gone", 0)]


def test_full_scope_lists_every_changeset_in_the_store(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    source = IcebergChangesetSource(_store(adapter_store_with_records))

    assert source.changesets == [("changeset-1", 1), ("changeset-2", 3)]


def test_changesets_written_after_the_pinned_snapshot_are_not_listed(
    adapter_store_with_records: AdapterStoreFactory,
    temporary_table: IcebergTable,
) -> None:
    store = _store(adapter_store_with_records)
    pinned = store.current_snapshot_id()
    temporary_table.append(
        adapter_records_to_table(
            [{"id": "rec005", "content": "e", "changeset": "changeset-3"}],
            namespace="test_namespace",
        )
    )

    source = IcebergChangesetSource(store, snapshot_id=pinned)

    assert source.snapshot_id == pinned
    assert [changeset for changeset, _ in source.changesets] == [
        "changeset-1",
        "changeset-2",
    ]
