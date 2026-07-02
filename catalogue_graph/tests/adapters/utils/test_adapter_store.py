"""
Tests for general AdapterStore utility methods.

These tests cover methods that are not specific to either incremental_update or snapshot_sync:
- get_all_records
- get_records_by_changeset
"""

from datetime import UTC, datetime
from operator import itemgetter
from typing import cast

from pyiceberg.table import Table as IcebergTable

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from tests.adapters.conftest import AdapterStoreFactory, adapter_records_to_table

# =============================================================================
# get_all_records tests
# =============================================================================


def test_get_all_records_empty_table(temporary_table: IcebergTable) -> None:
    """When the table is empty, get_all_records returns an empty Arrow table."""
    client = AdapterStore(temporary_table, "test_namespace")
    all_records = client.get_all_records()
    assert all_records.num_rows == 0


def test_get_all_records_returns_all_non_deleted(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """get_active_namespace_records returns all records where deleted is null or False."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "active record"},
            {"id": "rec002", "content": "another active", "deleted": False},
            {"id": "rec003", "content": "deleted record", "deleted": True},
        ]
    )

    all_records = client.get_active_namespace_records()

    ids = cast(list[str], all_records.column("id").to_pylist())
    assert sorted(ids) == ["rec001", "rec002"]


def test_get_all_records_include_deleted_true(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """get_all_records returns all records, including deleted ones."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "active record"},
            {"id": "rec002", "content": "deleted record", "deleted": True},
        ]
    )

    all_records = client.get_all_records()

    assert all_records.num_rows == 2
    ids = set(all_records.column("id").to_pylist())
    assert ids == {"rec001", "rec002"}


def test_get_all_records_excludes_deleted_by_default(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """get_all_records excludes deleted records by default (deleted=True)."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "active"},
            {
                "id": "rec002",
                "content": "deleted with content preserved",
                "deleted": True,
            },
        ]
    )

    all_records = client.get_active_namespace_records()

    assert all_records.num_rows == 1
    row = all_records.to_pylist()[0]
    assert row["id"] == "rec001"


def test_get_all_records_multiple_namespaces(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """get_all_records returns records from all namespaces."""
    client = adapter_store_with_records(
        [
            {"namespace": "namespace_a", "id": "rec001", "content": "from A"},
            {"namespace": "namespace_b", "id": "rec002", "content": "from B"},
        ]
    )

    all_records = client.get_all_records()

    assert all_records.num_rows == 2
    namespaces = set(all_records.column("namespace").to_pylist())
    assert namespaces == {"namespace_a", "namespace_b"}


# =============================================================================
# get_records_by_changeset tests
# =============================================================================


def test_get_records_by_changeset_returns_matching_records(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """get_records_by_changeset returns only records with the specified changeset."""
    changeset_1 = "changeset-aaa-111"
    changeset_2 = "changeset-bbb-222"

    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "first", "changeset": changeset_1},
            {"id": "rec002", "content": "second", "changeset": changeset_1},
            {"id": "rec003", "content": "third", "changeset": changeset_2},
        ]
    )

    # Query changeset_1
    result_1 = client.get_records_by_changeset(changeset_1)
    assert result_1.num_rows == 2
    ids_1 = set(result_1.column("id").to_pylist())
    assert ids_1 == {"rec001", "rec002"}

    # Query changeset_2
    result_2 = client.get_records_by_changeset(changeset_2)
    assert result_2.num_rows == 1
    ids_2 = set(result_2.column("id").to_pylist())
    assert ids_2 == {"rec003"}


def test_get_records_by_changeset_nonexistent_returns_empty(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """get_records_by_changeset returns empty table for non-existent changeset."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "hello", "changeset": "existing-changeset"},
        ]
    )

    result = client.get_records_by_changeset("non-existent-changeset")

    assert result.num_rows == 0


def test_get_records_by_changeset_empty_table(
    temporary_table: IcebergTable,
) -> None:
    """get_records_by_changeset on empty table returns empty result."""
    client = AdapterStore(temporary_table, "test_namespace")
    result = client.get_records_by_changeset("any-changeset")

    assert result.num_rows == 0


def test_get_records_by_changeset_includes_deleted_records(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """get_records_by_changeset returns deleted records (no filtering by deleted flag)."""
    changeset_id = "changeset-with-deletions"

    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "active", "changeset": changeset_id},
            {
                "id": "rec002",
                "content": "deleted but in changeset",
                "deleted": True,
                "changeset": changeset_id,
            },
        ]
    )

    result = client.get_records_by_changeset(changeset_id)

    # Should include both active and deleted records
    assert result.num_rows == 2
    ids = set(result.column("id").to_pylist())
    assert ids == {"rec001", "rec002"}


# =============================================================================
# get_changeset_record_ids tests
# =============================================================================


def test_get_changeset_record_ids_returns_ids_for_requested_changesets(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """Only ids from the requested changesets are returned."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "first", "changeset": "changeset-a"},
            {"id": "rec002", "content": "second", "changeset": "changeset-a"},
            {"id": "rec003", "content": "third", "changeset": "changeset-b"},
            {"id": "rec004", "content": "fourth", "changeset": "changeset-c"},
        ]
    )

    ids = client.get_changeset_record_ids(["changeset-a", "changeset-b"]).ids

    assert sorted(ids) == ["rec001", "rec002", "rec003"]


def test_get_changeset_record_ids_includes_deleted_records(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """Deleted rows in a changeset are included (no filtering by deleted flag)."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "active", "changeset": "changeset-a"},
            {
                "id": "rec002",
                "content": "deleted",
                "deleted": True,
                "changeset": "changeset-a",
            },
        ]
    )

    ids = client.get_changeset_record_ids(["changeset-a"]).ids

    assert sorted(ids) == ["rec001", "rec002"]


def test_get_changeset_record_ids_scoped_to_namespace(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """Only ids in the store's namespace are returned for a shared changeset id."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "mine", "changeset": "changeset-a"},
            {
                "id": "rec002",
                "content": "other",
                "changeset": "changeset-a",
                "namespace": "other_namespace",
            },
        ]
    )

    ids = client.get_changeset_record_ids(["changeset-a"]).ids

    assert ids == ["rec001"]


def test_get_changeset_record_ids_nonexistent_changeset_returns_empty(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """A changeset with no rows returns an empty id list."""
    client = adapter_store_with_records(
        [{"id": "rec001", "content": "first", "changeset": "changeset-a"}]
    )

    result = client.get_changeset_record_ids(["nonexistent"])

    assert result.ids == []
    assert result.min_last_modified is None


def test_get_changeset_record_ids_pins_snapshot(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """Ids are read at the pinned snapshot, excluding later appends."""
    client = adapter_store_with_records(
        [{"id": "rec001", "content": "first", "changeset": "changeset-a"}]
    )
    pinned_snapshot_id = client.current_snapshot_id()

    client.table.append(
        adapter_records_to_table(
            [{"id": "rec002", "content": "later", "changeset": "changeset-a"}]
        )
    )

    pinned_ids = client.get_changeset_record_ids(
        ["changeset-a"], pinned_snapshot_id
    ).ids
    current_ids = client.get_changeset_record_ids(["changeset-a"]).ids

    assert pinned_ids == ["rec001"]
    assert sorted(current_ids) == ["rec001", "rec002"]


# =============================================================================
# get_records_by_ids tests
# =============================================================================


def test_get_records_by_ids_matches_changeset_read_including_deleted(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """The id-based read returns the same rows as the changeset read, including
    a deleted row with its preserved content."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "active", "changeset": "changeset-a"},
            {
                "id": "rec002",
                "content": "deleted with content preserved",
                "deleted": True,
                "changeset": "changeset-a",
            },
            {"id": "rec003", "content": "other changeset", "changeset": "changeset-b"},
        ]
    )

    ids = client.get_changeset_record_ids(["changeset-a"]).ids
    by_ids = client.get_records_by_ids(ids).to_pylist()
    by_changeset = client.get_records_by_changeset("changeset-a").to_pylist()

    sort_key = itemgetter("id")
    assert sorted(by_ids, key=sort_key) == sorted(by_changeset, key=sort_key)
    deleted_row = next(row for row in by_ids if row["id"] == "rec002")
    assert deleted_row["deleted"] is True
    assert deleted_row["content"] == "deleted with content preserved"


def test_get_records_by_ids_empty_ids_returns_empty(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """An empty id list returns an empty table without erroring."""
    client = adapter_store_with_records(
        [{"id": "rec001", "content": "first", "changeset": "changeset-a"}]
    )

    assert client.get_records_by_ids([]).num_rows == 0


def test_get_changeset_record_ids_returns_min_last_modified(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """The minimum last_modified across the changeset's rows is returned."""
    earlier = datetime(2026, 7, 1, 9, 0, tzinfo=UTC)
    later = datetime(2026, 7, 1, 10, 0, tzinfo=UTC)
    client = adapter_store_with_records(
        [
            {
                "id": "rec001",
                "content": "first",
                "changeset": "changeset-a",
                "last_modified": later,
            },
            {
                "id": "rec002",
                "content": "second",
                "changeset": "changeset-a",
                "last_modified": earlier,
            },
        ]
    )

    result = client.get_changeset_record_ids(["changeset-a"])

    assert result.min_last_modified == earlier


def test_get_records_by_ids_with_updated_since_bound_returns_all_target_rows(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """Using the changeset's own minimum last_modified as the bound returns
    exactly the same rows as the unbounded id read."""
    earlier = datetime(2026, 7, 1, 9, 0, tzinfo=UTC)
    later = datetime(2026, 7, 1, 10, 0, tzinfo=UTC)
    client = adapter_store_with_records(
        [
            {
                "id": "rec001",
                "content": "first",
                "changeset": "changeset-a",
                "last_modified": earlier,
            },
            {
                "id": "rec002",
                "content": "deleted",
                "deleted": True,
                "changeset": "changeset-a",
                "last_modified": later,
            },
            {
                "id": "rec003",
                "content": "older row outside the changeset",
                "changeset": "changeset-b",
                "last_modified": datetime(2026, 1, 1, tzinfo=UTC),
            },
        ]
    )

    ids, min_last_modified = client.get_changeset_record_ids(["changeset-a"])
    bounded = client.get_records_by_ids(ids, updated_since=min_last_modified)
    unbounded = client.get_records_by_ids(ids)

    sort_key = itemgetter("id")
    assert sorted(bounded.to_pylist(), key=sort_key) == sorted(
        unbounded.to_pylist(), key=sort_key
    )
    bounded_ids = cast(list[str], bounded.column("id").to_pylist())
    assert sorted(bounded_ids) == ["rec001", "rec002"]


# =============================================================================
# stream_active_namespace_records tests
# =============================================================================


def test_stream_active_namespace_records_matches_eager_read(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """Streaming yields exactly the same rows as the eager read."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "first"},
            {"id": "rec002", "content": "second", "deleted": False},
            {"id": "rec003", "content": "third"},
        ]
    )

    streamed = [
        row
        for batch in client.stream_active_namespace_records()
        for row in batch.to_pylist()
    ]
    eager = client.get_active_namespace_records().to_pylist()

    sort_key = itemgetter("id")
    assert sorted(streamed, key=sort_key) == sorted(eager, key=sort_key)


def test_stream_active_namespace_records_excludes_deleted(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """Streaming returns records where deleted is null or False, excluding deleted ones."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "active record"},
            {"id": "rec002", "content": "another active", "deleted": False},
            {"id": "rec003", "content": "deleted record", "deleted": True},
        ]
    )

    streamed_ids = [
        row["id"]
        for batch in client.stream_active_namespace_records()
        for row in batch.to_pylist()
    ]

    assert sorted(streamed_ids) == ["rec001", "rec002"]


def test_stream_active_namespace_records_honours_snapshot_id(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """Streaming with a pinned snapshot excludes rows appended after the snapshot."""
    client = adapter_store_with_records(
        [
            {"id": "rec001", "content": "first"},
            {"id": "rec002", "content": "second"},
        ]
    )
    pinned_snapshot_id = client.current_snapshot_id()

    client.table.append(adapter_records_to_table([{"id": "rec003", "content": "new"}]))

    pinned_ids = [
        row["id"]
        for batch in client.stream_active_namespace_records(pinned_snapshot_id)
        for row in batch.to_pylist()
    ]
    current_ids = [
        row["id"]
        for batch in client.stream_active_namespace_records()
        for row in batch.to_pylist()
    ]

    assert sorted(pinned_ids) == ["rec001", "rec002"]
    assert sorted(current_ids) == ["rec001", "rec002", "rec003"]


def test_stream_active_namespace_records_empty_table(
    temporary_table: IcebergTable,
) -> None:
    """Streaming an empty table yields no rows, with the store's Arrow schema."""
    client = AdapterStore(temporary_table, "test_namespace")

    reader = client.stream_active_namespace_records()

    assert reader.schema == ADAPTER_STORE_ARROW_SCHEMA
    assert sum(batch.num_rows for batch in reader) == 0


def test_stream_active_namespace_records_batches_match_store_schema(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    """Streamed batches carry the store's Arrow schema."""
    client = adapter_store_with_records([{"id": "rec001", "content": "first"}])

    batches = list(client.stream_active_namespace_records())

    assert len(batches) > 0
    assert all(batch.schema == ADAPTER_STORE_ARROW_SCHEMA for batch in batches)
