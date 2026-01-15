"""
Tests for general AdapterStore utility methods.

These tests cover methods that are not specific to either incremental_update or snapshot_sync:
- get_all_records
- get_records_by_changeset
"""

from datetime import UTC, datetime

import pyarrow as pa
from pyiceberg.table import Table as IcebergTable

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA


def data_to_table(records: list[dict], namespace: str = "test_namespace") -> pa.Table:
    """Helper to create a PyArrow table from a list of records."""
    for record in records:
        record["namespace"] = namespace
        record.setdefault("last_modified", None)
        record.setdefault("deleted", None)
    return pa.Table.from_pylist(records, schema=ARROW_SCHEMA)


# =============================================================================
# get_all_records tests
# =============================================================================


def test_get_all_records_empty_table(temporary_table: IcebergTable) -> None:
    """When the table is empty, get_all_records returns an empty Arrow table."""
    client = AdapterStore(temporary_table)
    all_records = client.get_all_records()
    assert all_records.num_rows == 0


def test_get_all_records_returns_all_non_deleted(
    temporary_table: IcebergTable,
) -> None:
    """get_all_records returns all records where deleted is null or False."""
    # Insert records with various deleted states
    records = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "rec001",
                "content": "active record",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": None,
            },
            {
                "namespace": "test_namespace",
                "id": "rec002",
                "content": "another active",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": False,
            },
            {
                "namespace": "test_namespace",
                "id": "rec003",
                "content": "deleted record",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": True,
            },
        ],
        schema=ARROW_SCHEMA,
    )
    temporary_table.append(records)

    client = AdapterStore(temporary_table)
    all_records = client.get_all_records()

    assert all_records.num_rows == 2
    ids = set(all_records.column("id").to_pylist())
    assert ids == {"rec001", "rec002"}


def test_get_all_records_include_deleted_true(
    temporary_table: IcebergTable,
) -> None:
    """get_all_records with include_deleted=True returns all records including deleted ones."""
    records = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "rec001",
                "content": "active record",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": None,
            },
            {
                "namespace": "test_namespace",
                "id": "rec002",
                "content": "deleted record",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": True,
            },
        ],
        schema=ARROW_SCHEMA,
    )
    temporary_table.append(records)

    client = AdapterStore(temporary_table)
    all_records = client.get_all_records(include_deleted=True)

    assert all_records.num_rows == 2
    ids = set(all_records.column("id").to_pylist())
    assert ids == {"rec001", "rec002"}


def test_get_all_records_excludes_deleted_by_default(
    temporary_table: IcebergTable,
) -> None:
    """get_all_records excludes deleted records by default (deleted=True)."""
    records = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "rec001",
                "content": "active",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": None,
            },
            {
                "namespace": "test_namespace",
                "id": "rec002",
                "content": "deleted with content preserved",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": True,
            },
        ],
        schema=ARROW_SCHEMA,
    )
    temporary_table.append(records)

    client = AdapterStore(temporary_table)
    all_records = client.get_all_records()  # Default: include_deleted=False

    assert all_records.num_rows == 1
    row = all_records.to_pylist()[0]
    assert row["id"] == "rec001"


def test_get_all_records_multiple_namespaces(
    temporary_table: IcebergTable,
) -> None:
    """get_all_records returns records from all namespaces."""
    records = pa.Table.from_pylist(
        [
            {
                "namespace": "namespace_a",
                "id": "rec001",
                "content": "from A",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": None,
            },
            {
                "namespace": "namespace_b",
                "id": "rec002",
                "content": "from B",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": None,
            },
        ],
        schema=ARROW_SCHEMA,
    )
    temporary_table.append(records)

    client = AdapterStore(temporary_table)
    all_records = client.get_all_records()

    assert all_records.num_rows == 2
    namespaces = set(all_records.column("namespace").to_pylist())
    assert namespaces == {"namespace_a", "namespace_b"}


# =============================================================================
# get_records_by_changeset tests
# =============================================================================


def test_get_records_by_changeset_returns_matching_records(
    temporary_table: IcebergTable,
) -> None:
    """get_records_by_changeset returns only records with the specified changeset."""
    changeset_1 = "changeset-aaa-111"
    changeset_2 = "changeset-bbb-222"

    # Manually insert records with different changesets
    records = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "rec001",
                "content": "first",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": None,
                "changeset": changeset_1,
            },
            {
                "namespace": "test_namespace",
                "id": "rec002",
                "content": "second",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": None,
                "changeset": changeset_1,
            },
            {
                "namespace": "test_namespace",
                "id": "rec003",
                "content": "third",
                "last_modified": datetime(2025, 1, 2, tzinfo=UTC),
                "deleted": None,
                "changeset": changeset_2,
            },
        ],
        schema=ARROW_SCHEMA,
    )
    temporary_table.append(records)

    client = AdapterStore(temporary_table)

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
    temporary_table: IcebergTable,
) -> None:
    """get_records_by_changeset returns empty table for non-existent changeset."""
    # Insert some records
    records = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "rec001",
                "content": "hello",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": None,
                "changeset": "existing-changeset",
            },
        ],
        schema=ARROW_SCHEMA,
    )
    temporary_table.append(records)

    client = AdapterStore(temporary_table)
    result = client.get_records_by_changeset("non-existent-changeset")

    assert result.num_rows == 0


def test_get_records_by_changeset_empty_table(
    temporary_table: IcebergTable,
) -> None:
    """get_records_by_changeset on empty table returns empty result."""
    client = AdapterStore(temporary_table)
    result = client.get_records_by_changeset("any-changeset")

    assert result.num_rows == 0


def test_get_records_by_changeset_includes_deleted_records(
    temporary_table: IcebergTable,
) -> None:
    """get_records_by_changeset returns deleted records (no filtering by deleted flag)."""
    changeset_id = "changeset-with-deletions"

    records = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "rec001",
                "content": "active",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": None,
                "changeset": changeset_id,
            },
            {
                "namespace": "test_namespace",
                "id": "rec002",
                "content": "deleted but in changeset",
                "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
                "deleted": True,
                "changeset": changeset_id,
            },
        ],
        schema=ARROW_SCHEMA,
    )
    temporary_table.append(records)

    client = AdapterStore(temporary_table)
    result = client.get_records_by_changeset(changeset_id)

    # Should include both active and deleted records
    assert result.num_rows == 2
    ids = set(result.column("id").to_pylist())
    assert ids == {"rec001", "rec002"}
