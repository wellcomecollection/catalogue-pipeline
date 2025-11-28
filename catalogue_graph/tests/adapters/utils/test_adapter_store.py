"""
Tests covering the update behaviour of the AdapterStore
"""

from collections.abc import Collection
from typing import Any

import pyarrow as pa
from pyiceberg.expressions import EqualTo, In, IsNull, Not
from pyiceberg.table import Table as IcebergTable

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA


def data_to_namespaced_table(
    unqualified_data: list[dict[str, Any]], namespace: str = "test_namespace"
) -> pa.Table:
    data = []
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


def test_noop(temporary_table: IcebergTable) -> None:
    """
    When there are no updates to perform, nothing happens
    """
    data = data_to_namespaced_table([{"id": "eb0001", "content": "hello"}])
    temporary_table.append(data)
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(data, "test_namespace")
    # No Changeset identifier is returned
    assert changeset is None
    # The data is the same as before the update
    assert (
        temporary_table.scan(selected_fields=("namespace", "id", "content"))
        .to_arrow()
        .cast(ARROW_SCHEMA)
        .equals(data)
    )
    # No changeset identifiers have been added
    assert not temporary_table.scan(row_filter=Not(IsNull("changeset"))).to_arrow()


def test_undelete(temporary_table: IcebergTable) -> None:
    """
    Given a table with a record that has been deleted
    When a record with the same identifier is present in new data
    The record is successfully undeleted.

        This test ensures that we do not run the risk of creating an
        infinite number of records with the same identifier if the
        provider deletes and restores access to that resource.
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
    # No Changeset identifier is returned
    assert changeset is not None
    # The data is the same as before the update
    as_pa = (
        temporary_table.scan(selected_fields=("id", "content", "changeset"))
        .to_arrow()
        .sort_by("id")
        .to_pylist()
    )
    # i.e. the same number of records are present before and after the change
    assert len(as_pa) == 2
    assert as_pa[1] == {"id": "eb0002", "content": "world!", "changeset": changeset}


def test_new_table(temporary_table: IcebergTable) -> None:
    """
    Given an environment with no data
    When an update is applied
    All the new data is stored
    :return:
    """

    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hej"},
            {"id": "eb0002", "content": "boo!"},
            {"id": "eb0003", "content": "alle sammen"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset_id = client.snapshot_sync(new_data, "test_namespace")
    assert changeset_id is not None  # Type assertion for mypy
    assert (
        temporary_table.scan().to_arrow()
        == temporary_table.scan(
            row_filter=EqualTo("changeset", changeset_id)
        ).to_arrow()
    )
    assert len(temporary_table.scan().to_arrow()) == 3


def test_update_records(temporary_table: IcebergTable) -> None:
    """
    Given an existing iceberg table
    And an update file with the same records
    And some of those records are different
    When the update is applied
    Then the records will have been changed
    And the changed rows will be identifiably grouped by a changeset property
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {
                    "id": "eb0001",
                    "content": "hello",
                },
                {
                    "id": "eb0002",
                    "content": "boo!",
                },
                {
                    "id": "eb0003",
                    "content": "world",
                },
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
    changeset_id = client.snapshot_sync(new_data, "test_namespace")
    assert changeset_id is not None  # Type assertion for mypy
    expected_changes = {"eb0001", "eb0003"}
    changed_rows = temporary_table.scan(
        row_filter=In("id", expected_changes), selected_fields=("id",)
    ).to_arrow()
    changeset_rows = temporary_table.scan(
        row_filter=EqualTo("changeset", changeset_id), selected_fields=("id",)
    ).to_arrow()

    assert_row_identifiers(changeset_rows, expected_changes)
    assert changed_rows == changeset_rows


def test_insert_records(temporary_table: IcebergTable) -> None:
    """
    Given an existing iceberg table
    And an update file with the same records
    And some new records
    When the update is applied
    Then the new records will be added
    And the new rows will be identifiably grouped by a changeset property
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
    changeset_id = client.snapshot_sync(new_data, "test_namespace")
    assert changeset_id is not None  # Type assertion for mypy
    expected_insertions = {"eb0002", "eb0099"}
    inserted_rows = temporary_table.scan(
        row_filter=In("id", expected_insertions), selected_fields=("id",)
    ).to_arrow()
    changeset_rows = temporary_table.scan(
        row_filter=EqualTo("changeset", changeset_id), selected_fields=("id",)
    ).to_arrow()

    assert_row_identifiers(changeset_rows, expected_insertions)
    assert inserted_rows == changeset_rows


def test_delete_records(temporary_table: IcebergTable) -> None:
    """
    Given an existing iceberg table
    And an update file with some records removed
    When the update is applied
    Then the removed records will be marked as deleted
    And the new rows will be identifiably grouped by a changeset property
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
    changeset_id = client.snapshot_sync(new_data, "test_namespace")
    assert changeset_id is not None  # Type assertion for mypy
    expected_deletions = {"eb0002", "eb0099"}
    deleted_rows = temporary_table.scan(
        row_filter=IsNull("content"), selected_fields=("id",)
    ).to_arrow()
    assert_row_identifiers(deleted_rows, expected_deletions)
    changeset_rows = temporary_table.scan(
        row_filter=EqualTo("changeset", changeset_id), selected_fields=("id",)
    ).to_arrow()

    assert_row_identifiers(changeset_rows, expected_deletions)


def test_all_actions(temporary_table: IcebergTable) -> None:
    """
    Given an existing Iceberg table
    And an update file with new, changed, absent and unchanged  records
    When the update is applied
    Then all the appropriate actions are taken
    And all the new, changed and deleted rows are identifiably grouped by a changeset property
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
    changeset_id = client.snapshot_sync(new_data, "test_namespace")
    assert changeset_id is not None  # Type assertion for mypy
    changeset_rows = temporary_table.scan(
        row_filter=EqualTo("changeset", changeset_id),
    ).to_arrow()
    assert len(changeset_rows) == 3
    rows_by_key = {row["id"]: row for row in changeset_rows.to_pylist()}
    assert rows_by_key[expected_deletion]["content"] is None
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
            "namespace": "test_namespace",
        }
    ]


def test_idempotent(temporary_table: IcebergTable) -> None:
    """
    Given an existing Iceberg table
    And an update with new, changed, absent and unchanged  records
    When the update is applied twice
    Then nothing happens the second time
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
    changeset_id = client.snapshot_sync(new_data, "test_namespace")
    assert changeset_id
    second_changeset_id = client.snapshot_sync(new_data, "test_namespace")
    assert second_changeset_id is None


def test_most_recent_changeset_preserved(temporary_table: IcebergTable) -> None:
    """
    Given an existing Iceberg table
    And two subsequent updates that change different records
    When the updates are applied
    Then each row's changeset id is the latest one that applied to it
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
    changeset_id = client.snapshot_sync(new_data, "test_namespace")
    assert changeset_id is not None  # Type assertion for mypy
    assert {"eb0003", "eb0004"} == set(
        temporary_table.scan(row_filter=EqualTo("changeset", changeset_id))
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
    newer_changeset_id = client.snapshot_sync(newer_data, "test_namespace")
    assert newer_changeset_id is not None  # Type assertion for mypy
    assert {"eb0003"} == set(
        temporary_table.scan(row_filter=EqualTo("changeset", newer_changeset_id))
        .to_arrow()
        .column("id")
        .to_pylist()
    )
    assert {"eb0004"} == set(
        temporary_table.scan(row_filter=EqualTo("changeset", changeset_id))
        .to_arrow()
        .column("id")
        .to_pylist()
    )


def test_get_records_by_changeset(temporary_table: IcebergTable) -> None:
    """
    Test that get_records_by_changeset correctly retrieves records for a specific changeset.
    """
    # Set up initial data
    initial_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0002", "content": "world"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset_id_1 = client.snapshot_sync(initial_data, "test_namespace")
    assert changeset_id_1 is not None  # Type assertion for mypy

    # Test retrieving records by first changeset
    records_changeset_1 = client.get_records_by_changeset(changeset_id_1)
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
    changeset_id_2 = client.snapshot_sync(additional_data, "test_namespace")
    assert changeset_id_2 is not None  # Type assertion for mypy

    # Test retrieving records by second changeset (should only include new records)
    records_changeset_2 = client.get_records_by_changeset(changeset_id_2)
    assert records_changeset_2.num_rows == 2
    ids_changeset_2 = set(records_changeset_2.column("id").to_pylist())
    assert ids_changeset_2 == {"eb0003", "eb0004"}

    # Test that changeset IDs are different
    assert changeset_id_1 != changeset_id_2

    # Test that first changeset records are still there
    records_changeset_1_after = client.get_records_by_changeset(changeset_id_1)
    assert records_changeset_1_after.num_rows == 2
    ids_changeset_1_after = set(records_changeset_1_after.column("id").to_pylist())
    assert ids_changeset_1_after == {"eb0001", "eb0002"}

    # Test retrieving with a non-existent changeset ID returns empty result
    empty_result = client.get_records_by_changeset("non-existent-changeset")
    assert empty_result.num_rows == 0


def test_get_all_records_empty(temporary_table: IcebergTable) -> None:
    """When the table is empty, get_all_records returns an empty Arrow table."""
    client = AdapterStore(temporary_table)
    all_records = client.get_all_records()
    assert all_records.num_rows == 0


def test_get_all_records_after_update(temporary_table: IcebergTable) -> None:
    """After update, default get_all_records excludes deleted rows."""
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
    changeset_id = client.snapshot_sync(new_data, "test_namespace")
    assert changeset_id is not None

    # get_all_records should EXCLUDE deleted by default -> eb0002 gone
    all_records = client.get_all_records()
    assert all_records.num_rows == 3
    rows = {row["id"]: row for row in all_records.to_pylist()}
    assert set(rows.keys()) == {"eb0001", "eb0003", "eb0004"}
    assert rows["eb0003"]["content"] == "god aften"  # updated
    assert rows["eb0004"]["content"] == "noswaith dda"  # inserted
    assert rows["eb0001"]["content"] == "hello"  # unchanged


def test_get_all_records_include_deleted_after_update(
    temporary_table: IcebergTable,
) -> None:
    """With include_deleted=True the deleted rows are present."""
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
    changeset_id = client.snapshot_sync(new_data, "test_namespace")
    assert changeset_id is not None
    all_with_deleted = client.get_all_records(include_deleted=True)
    assert all_with_deleted.num_rows == 4
    rows = {row["id"]: row for row in all_with_deleted.to_pylist()}
    assert set(rows.keys()) == {"eb0001", "eb0002", "eb0003", "eb0004"}
    assert rows["eb0002"]["content"] is None  # deleted present


def test_incremental_update_does_not_delete_missing_records(
    temporary_table: IcebergTable,
) -> None:
    """
    Given an existing Iceberg table
    And an incremental update that only contains a subset of records
    When the update is applied with incremental=True
    Then the missing records are NOT deleted
    """
    # Initial state: 2 records
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0002", "content": "world"},
            ]
        )
    )

    # Incremental update: only updates eb0001, does not include eb0002
    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello updated"},
        ]
    )

    client = AdapterStore(temporary_table)
    update = client.incremental_update(new_data, "test_namespace")
    assert update is not None

    # Verify eb0002 is still present and not deleted (content is not None)
    all_records = client.get_all_records()
    rows = {row["id"]: row for row in all_records.to_pylist()}

    assert "eb0002" in rows
    assert rows["eb0002"]["content"] == "world"  # Unchanged


def test_incremental_update_with_new_records(temporary_table: IcebergTable) -> None:
    """
    Given an existing Iceberg table
    And an incremental update that contains new records
    When the update is applied
    Then the new records are inserted
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
            ]
        )
    )

    new_data = data_to_namespaced_table(
        [
            {"id": "eb0002", "content": "world"},
        ]
    )

    client = AdapterStore(temporary_table)
    update = client.incremental_update(new_data, "test_namespace")
    assert update is not None

    all_records = client.get_all_records()
    rows = {row["id"]: row for row in all_records.to_pylist()}

    assert "eb0001" in rows
    assert "eb0002" in rows
    assert rows["eb0002"]["content"] == "world"


def test_incremental_update_mixed(temporary_table: IcebergTable) -> None:
    """
    Given an existing Iceberg table
    And an incremental update that contains both updates and new records
    When the update is applied
    Then the updates are applied and new records are inserted
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0002", "content": "world"},
            ]
        )
    )

    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello updated"},
            {"id": "eb0003", "content": "new record"},
        ]
    )

    client = AdapterStore(temporary_table)
    update = client.incremental_update(new_data, "test_namespace")
    assert update is not None

    all_records = client.get_all_records()
    rows = {row["id"]: row for row in all_records.to_pylist()}

    assert len(rows) == 3
    assert rows["eb0001"]["content"] == "hello updated"
    assert rows["eb0002"]["content"] == "world"
    assert rows["eb0003"]["content"] == "new record"


def test_incremental_update_does_not_touch_other_namespaces(
    temporary_table: IcebergTable,
) -> None:
    """
    Given an Iceberg table with data from multiple namespaces
    When an incremental update is applied to one namespace
    Then data in other namespaces is unaffected
    """
    # Add data for another namespace
    other_data = data_to_namespaced_table(
        [{"id": "ax0001", "content": "axiell data"}], "axiell_test"
    )
    temporary_table.append(other_data)

    # Add data for ebsco namespace
    ebsco_data = data_to_namespaced_table([{"id": "eb0001", "content": "ebsco data"}])
    temporary_table.append(ebsco_data)

    # Update ebsco data
    new_ebsco_data = data_to_namespaced_table(
        [{"id": "eb0001", "content": "ebsco updated"}]
    )

    client = AdapterStore(temporary_table)
    client.incremental_update(new_ebsco_data, "test_namespace")

    # Verify axiell data is untouched
    all_records = client.get_all_records()
    rows = {(row["namespace"], row["id"]): row for row in all_records.to_pylist()}

    assert ("axiell_test", "ax0001") in rows
    assert rows[("axiell_test", "ax0001")]["content"] == "axiell data"
    assert rows[("test_namespace", "eb0001")]["content"] == "ebsco updated"
