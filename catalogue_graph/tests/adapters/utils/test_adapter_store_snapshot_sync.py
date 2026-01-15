"""
Tests covering the snapshot_sync behaviour of the AdapterStore.

snapshot_sync performs a full replacement sync where the provided data
represents the complete desired state. Records not present in the new
data are marked as deleted.
"""

from collections.abc import Collection
from typing import Any

import pyarrow as pa
from pyiceberg.expressions import EqualTo, In, IsNull, Not
from pyiceberg.table import Table as IcebergTable

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA


def data_to_namespaced_table(
    unqualified_data: list[dict[str, Any]],
    namespace: str = "test_namespace",
) -> pa.Table:
    """
    Create an Arrow table with the repo-standard schema.
    """
    data: list[dict[str, Any]] = []
    for item in unqualified_data:
        new_item = item.copy()
        new_item["namespace"] = namespace
        data.append(new_item)

    return pa.Table.from_pylist(data, schema=ARROW_SCHEMA)


def assert_row_identifiers(rows: pa.Table, expected_ids: Collection[str]) -> None:
    """
    Assert that the given rows contain exactly the expected IDs.

    Args:
        rows: PyArrow table containing rows with an 'id' column
        expected_ids: Set or collection of expected ID values
    """
    actual_ids = set(rows.column("id").to_pylist())
    assert actual_ids == set(expected_ids)


def test_snapshot_sync_noop(temporary_table: IcebergTable) -> None:
    """
    Given a table with existing data
    When snapshot_sync is called with identical data
    Then no changeset is created and data remains unchanged
    """
    data = data_to_namespaced_table([{"id": "eb0001", "content": "hello"}])
    temporary_table.append(data)
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(data, "test_namespace")
    # No Changeset identifier is returned
    assert changeset is None
    # The data is the same as before the update
    expected_field_names = tuple(field.name for field in ARROW_SCHEMA)
    assert (
        temporary_table.scan(selected_fields=expected_field_names)
        .to_arrow()
        .cast(ARROW_SCHEMA)
        .equals(data)
    )
    # No changeset identifiers have been added
    assert not temporary_table.scan(row_filter=Not(IsNull("changeset"))).to_arrow()


def test_snapshot_sync_undelete(temporary_table: IcebergTable) -> None:
    """
    Given a table with a deleted record (content=None)
    When snapshot_sync includes that record with content
    Then the record is successfully undeleted

    This ensures we don't create duplicate records if a provider
    deletes and restores access to a resource.
    """
    data = data_to_namespaced_table(
        [{"id": "eb0001", "content": "hello"}, {"id": "eb0002", "content": None}]
    )
    temporary_table.append(data)
    new_data = data_to_namespaced_table(
        [{"id": "eb0001", "content": "hello"}, {"id": "eb0002", "content": "world!"}]
    )

    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "test_namespace")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0002"}

    as_pa = (
        temporary_table.scan(selected_fields=("id", "content", "changeset"))
        .to_arrow()
        .sort_by("id")
        .to_pylist()
    )
    # The same number of records are present before and after the change
    assert len(as_pa) == 2
    assert as_pa[1] == {
        "id": "eb0002",
        "content": "world!",
        "changeset": changeset.changeset_id,
    }


def test_snapshot_sync_new_table(temporary_table: IcebergTable) -> None:
    """
    Given an empty table
    When snapshot_sync is applied
    Then all new data is stored and tagged with a changeset
    """
    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hej"},
            {"id": "eb0002", "content": "boo!"},
            {"id": "eb0003", "content": "alle sammen"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "test_namespace")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0001", "eb0002", "eb0003"}
    assert (
        temporary_table.scan().to_arrow()
        == temporary_table.scan(
            row_filter=EqualTo("changeset", changeset.changeset_id)
        ).to_arrow()
    )
    assert len(temporary_table.scan().to_arrow()) == 3


def test_snapshot_sync_update_records(temporary_table: IcebergTable) -> None:
    """
    Given an existing table
    When snapshot_sync includes records with changed content
    Then the changed records are updated and tagged with a changeset
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0002", "content": "boo!"},
                {"id": "eb0003", "content": "world"},
            ]
        )
    )

    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hej"},
            {"id": "eb0002", "content": "boo!"},
            {"id": "eb0003", "content": "alle sammen"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "test_namespace")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0001", "eb0003"}
    expected_changes = {"eb0001", "eb0003"}
    changed_rows = temporary_table.scan(
        row_filter=In("id", expected_changes), selected_fields=("id",)
    ).to_arrow()
    changeset_rows = temporary_table.scan(
        row_filter=EqualTo("changeset", changeset.changeset_id),
        selected_fields=("id",),
    ).to_arrow()

    assert_row_identifiers(changeset_rows, expected_changes)
    assert changed_rows == changeset_rows


def test_snapshot_sync_insert_records(temporary_table: IcebergTable) -> None:
    """
    Given an existing table
    When snapshot_sync includes new records
    Then the new records are inserted and tagged with a changeset
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0003", "content": "world"},
            ]
        )
    )

    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0002", "content": "bonjour"},
            {"id": "eb0003", "content": "world"},
            {"id": "eb0099", "content": "tout le monde"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "test_namespace")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0002", "eb0099"}
    expected_insertions = {"eb0002", "eb0099"}
    inserted_rows = temporary_table.scan(
        row_filter=In("id", expected_insertions), selected_fields=("id",)
    ).to_arrow()
    changeset_rows = temporary_table.scan(
        row_filter=EqualTo("changeset", changeset.changeset_id),
        selected_fields=("id",),
    ).to_arrow()

    assert_row_identifiers(changeset_rows, expected_insertions)
    assert inserted_rows == changeset_rows


def test_snapshot_sync_delete_records(temporary_table: IcebergTable) -> None:
    """
    Given an existing table
    When snapshot_sync excludes records that were previously present
    Then those records are marked as deleted (content=None) and tagged with a changeset
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0002", "content": "bonjour"},
                {"id": "eb0003", "content": "world"},
                {"id": "eb0099", "content": "tout le monde"},
            ]
        )
    )

    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0003", "content": "world"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "test_namespace")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0002", "eb0099"}
    expected_deletions = {"eb0002", "eb0099"}
    deleted_rows = temporary_table.scan(
        row_filter=EqualTo("deleted", True), selected_fields=("id", "content")
    ).to_arrow()
    assert_row_identifiers(deleted_rows, expected_deletions)
    # Verify content is preserved on deleted records
    deleted_content = {row["id"]: row["content"] for row in deleted_rows.to_pylist()}
    assert deleted_content == {"eb0002": "bonjour", "eb0099": "tout le monde"}
    changeset_rows = temporary_table.scan(
        row_filter=EqualTo("changeset", changeset.changeset_id),
        selected_fields=("id",),
    ).to_arrow()

    assert_row_identifiers(changeset_rows, expected_deletions)


def test_snapshot_sync_all_actions(temporary_table: IcebergTable) -> None:
    """
    Given an existing table
    When snapshot_sync includes new, changed, absent and unchanged records
    Then inserts, updates, and deletes are all applied correctly
    And all modified rows are tagged with the same changeset
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0002", "content": "byebye"},
                {"id": "eb0003", "content": "greetings"},
            ]
        )
    )

    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0003", "content": "god aften"},
            {"id": "eb0004", "content": "noswaith dda"},
        ]
    )
    expected_deletion = "eb0002"
    expected_update = "eb0003"
    expected_insert = "eb0004"

    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "test_namespace")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {
        expected_deletion,
        expected_update,
        expected_insert,
    }
    changeset_rows = temporary_table.scan(
        row_filter=EqualTo("changeset", changeset.changeset_id),
    ).to_arrow()
    assert len(changeset_rows) == 3
    rows_by_key = {row["id"]: row for row in changeset_rows.to_pylist()}
    # Deleted records preserve content but are marked deleted
    assert rows_by_key[expected_deletion]["deleted"] is True
    assert rows_by_key[expected_deletion]["content"] == "byebye"
    assert rows_by_key[expected_update]["content"] == "god aften"
    assert rows_by_key[expected_insert]["content"] == "noswaith dda"
    # all rows in the changeset have the same last modified time
    # which is not None
    assert (
        rows_by_key[expected_deletion]["last_modified"]
        == rows_by_key[expected_update]["last_modified"]
        == rows_by_key[expected_insert]["last_modified"]
        is not None
    )
    # And the remaining value is unchanged
    assert temporary_table.scan(
        row_filter=IsNull("changeset")
    ).to_arrow().to_pylist() == [
        {
            "id": "eb0001",
            "content": "hello",
            "changeset": None,
            "last_modified": None,
            "deleted": None,
            "namespace": "test_namespace",
        }
    ]


def test_snapshot_sync_idempotent(temporary_table: IcebergTable) -> None:
    """
    Given an existing table
    When the same snapshot_sync is applied twice
    Then the second call is a no-op and returns None
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0002", "content": "byebye"},
                {"id": "eb0003", "content": "greetings"},
            ]
        )
    )
    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0003", "content": "god aften"},
            {"id": "eb0004", "content": "noswaith dda"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "test_namespace")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0002", "eb0003", "eb0004"}
    second_changeset = client.snapshot_sync(new_data, "test_namespace")
    assert second_changeset is None


def test_snapshot_sync_most_recent_changeset_preserved(
    temporary_table: IcebergTable,
) -> None:
    """
    Given an existing table
    When multiple snapshot_syncs are applied that change different records
    Then each row's changeset id reflects the most recent sync that modified it
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0003", "content": "greetings"},
            ]
        )
    )

    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0003", "content": "god aften"},
            {"id": "eb0004", "content": "noswaith dda"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "test_namespace")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0003", "eb0004"}
    assert {"eb0003", "eb0004"} == set(
        temporary_table.scan(row_filter=EqualTo("changeset", changeset.changeset_id))
        .to_arrow()
        .column("id")
        .to_pylist()
    )
    newer_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0003", "content": "guten abend"},
            {"id": "eb0004", "content": "noswaith dda"},
        ]
    )
    newer_changeset = client.snapshot_sync(newer_data, "test_namespace")
    assert newer_changeset is not None
    assert set(newer_changeset.updated_record_ids) == {"eb0003"}
    assert {"eb0003"} == set(
        temporary_table.scan(
            row_filter=EqualTo("changeset", newer_changeset.changeset_id)
        )
        .to_arrow()
        .column("id")
        .to_pylist()
    )
    assert {"eb0004"} == set(
        temporary_table.scan(row_filter=EqualTo("changeset", changeset.changeset_id))
        .to_arrow()
        .column("id")
        .to_pylist()
    )


def test_snapshot_sync_get_records_by_changeset(temporary_table: IcebergTable) -> None:
    """
    When get_records_by_changeset is called after snapshot_sync
    Then it correctly retrieves records for each specific changeset
    """
    # Set up initial data
    initial_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0002", "content": "world"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset_1 = client.snapshot_sync(initial_data, "test_namespace")
    assert changeset_1 is not None
    assert set(changeset_1.updated_record_ids) == {"eb0001", "eb0002"}

    # Test retrieving records by first changeset
    records_changeset_1 = client.get_records_by_changeset(changeset_1.changeset_id)
    assert records_changeset_1.num_rows == 2
    ids_changeset_1 = set(records_changeset_1.column("id").to_pylist())
    assert ids_changeset_1 == {"eb0001", "eb0002"}

    # Add completely new records (no updates to existing ones)
    additional_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},  # Existing record, no change
            {"id": "eb0002", "content": "world"},  # Existing record, no change
            {"id": "eb0003", "content": "new record"},  # New record
            {"id": "eb0004", "content": "another new record"},  # New record
        ]
    )
    changeset_2 = client.snapshot_sync(additional_data, "test_namespace")
    assert changeset_2 is not None
    assert set(changeset_2.updated_record_ids) == {"eb0003", "eb0004"}

    # Test retrieving records by second changeset (should only include new records)
    records_changeset_2 = client.get_records_by_changeset(changeset_2.changeset_id)
    assert records_changeset_2.num_rows == 2
    ids_changeset_2 = set(records_changeset_2.column("id").to_pylist())
    assert ids_changeset_2 == {"eb0003", "eb0004"}

    # Test that changeset IDs are different
    assert changeset_1.changeset_id != changeset_2.changeset_id

    # Test that first changeset records are still there
    records_changeset_1_after = client.get_records_by_changeset(
        changeset_1.changeset_id
    )
    assert records_changeset_1_after.num_rows == 2
    ids_changeset_1_after = set(records_changeset_1_after.column("id").to_pylist())
    assert ids_changeset_1_after == {"eb0001", "eb0002"}

    # Test retrieving with a non-existent changeset ID returns empty result
    empty_result = client.get_records_by_changeset("non-existent-changeset")
    assert empty_result.num_rows == 0


def test_snapshot_sync_get_all_records_after_update(
    temporary_table: IcebergTable,
) -> None:
    """
    After snapshot_sync, get_all_records excludes deleted rows by default
    """
    # Initial data
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0002", "content": "byebye"},
                {"id": "eb0003", "content": "greetings"},
            ]
        )
    )

    # New data deletes eb0002 (by absence), updates eb0003, inserts eb0004, leaves eb0001 unchanged
    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0003", "content": "god aften"},
            {"id": "eb0004", "content": "noswaith dda"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "test_namespace")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0002", "eb0003", "eb0004"}

    # get_all_records should EXCLUDE deleted by default -> eb0002 gone
    all_records = client.get_all_records()
    assert all_records.num_rows == 3
    rows = {row["id"]: row for row in all_records.to_pylist()}
    assert set(rows.keys()) == {"eb0001", "eb0003", "eb0004"}
    assert rows["eb0003"]["content"] == "god aften"  # updated
    assert rows["eb0004"]["content"] == "noswaith dda"  # inserted
    assert rows["eb0001"]["content"] == "hello"  # unchanged


def test_snapshot_sync_get_all_records_include_deleted(
    temporary_table: IcebergTable,
) -> None:
    """
    After snapshot_sync, get_all_records with include_deleted=True includes deleted rows
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0002", "content": "byebye"},
                {"id": "eb0003", "content": "greetings"},
            ]
        )
    )
    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0003", "content": "god aften"},
            {"id": "eb0004", "content": "noswaith dda"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "test_namespace")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0002", "eb0003", "eb0004"}
    all_with_deleted = client.get_all_records(include_deleted=True)
    assert all_with_deleted.num_rows == 4
    rows = {row["id"]: row for row in all_with_deleted.to_pylist()}
    assert set(rows.keys()) == {"eb0001", "eb0002", "eb0003", "eb0004"}
    # Deleted records preserve content but are marked deleted
    assert rows["eb0002"]["deleted"] is True
    assert rows["eb0002"]["content"] == "byebye"


def test_snapshot_sync_raises_on_non_castable_schema(
    temporary_table: IcebergTable,
) -> None:
    """snapshot_sync should fail fast if the adapter hands us a table with the wrong schema."""
    import pytest

    # Missing the required 'deleted' field from ARROW_SCHEMA.
    bad_fields: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    bad_schema = pa.schema(bad_fields)

    bad_table = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "hello",
                "last_modified": None,
            }
        ],
        schema=bad_schema,
    )

    client = AdapterStore(temporary_table)
    with pytest.raises(ValueError, match=r"snapshot_sync.*ARROW_SCHEMA"):
        client.snapshot_sync(bad_table, "test_namespace")
