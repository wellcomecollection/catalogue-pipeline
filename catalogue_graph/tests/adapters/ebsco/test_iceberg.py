"""
Tests covering the update behaviour of the iceberg ebsco adapter
"""

from typing import Any

import pyarrow as pa
from pyiceberg.expressions import EqualTo, In, IsNull, Not
from pyiceberg.table import Table as IcebergTable

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA

from .helpers import assert_row_identifiers
from .helpers import data_to_namespaced_table as _data_to_namespaced_table_helper


# Override the default namespace for these tests
def data_to_namespaced_table(
    unqualified_data: list[dict[str, Any]], *, add_timestamp: bool = False
) -> pa.Table:
    return _data_to_namespaced_table_helper(
        unqualified_data, "ebsco_test", add_timestamp=add_timestamp
    )


def test_noop(temporary_table: IcebergTable) -> None:
    """
    When there are no updates to perform, nothing happens
    """
    data = data_to_namespaced_table([{"id": "eb0001", "content": "hello"}])
    temporary_table.append(data)
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(data, "ebsco_test")
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
    changeset = client.snapshot_sync(new_data, "ebsco_test")
    # Changeset identifier is returned
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0002"}
    # The data is the same as before the update
    as_pa = (
        temporary_table.scan(selected_fields=("id", "content", "changeset"))
        .to_arrow()
        .sort_by("id")
        .to_pylist()
    )
    # i.e. the same number of records are present before and after the change
    assert len(as_pa) == 2
    assert as_pa[1] == {
        "id": "eb0002",
        "content": "world!",
        "changeset": changeset.changeset_id,
    }


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
    changeset = client.snapshot_sync(new_data, "ebsco_test")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0001", "eb0002", "eb0003"}
    assert (
        temporary_table.scan().to_arrow()
        == temporary_table.scan(
            row_filter=EqualTo("changeset", changeset.changeset_id)
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
    changeset = client.snapshot_sync(new_data, "ebsco_test")
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
    changeset = client.snapshot_sync(new_data, "ebsco_test")
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


def test_delete_records(temporary_table: IcebergTable) -> None:
    """
    Given an existing iceberg table
    And an update file with some records missing
    When the update is applied
    Then the missing records will be marked as deleted
    And the content of those records will have been removed
    And the changed rows are identifiably grouped by a changeset property

    Deletion must be at least semi-soft, as deleted records are simply absent in the data from
    the supplier, but the pipeline model downstream of here operates by being told which records have changed.
    Those records are then consulted and an appropriate action taken.

    This also allows us to replay a deletion if something fails
    downstream of here.
    If the row is completely deleted, then we have no way of knowing what action to take in the ongoing pipeline.
    """
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
                {"id": "eb0002", "content": "byebye"},
                {"id": "eb0003", "content": "greetings"},
                {"id": "eb0099", "content": "seeya"},
            ]
        )
    )
    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "hello"},
            {"id": "eb0003", "content": "greetings"},
        ]
    )
    client = AdapterStore(temporary_table)
    changeset = client.snapshot_sync(new_data, "ebsco_test")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0002", "eb0099"}
    expected_deletions = {"eb0002", "eb0099"}
    deleted_rows = temporary_table.scan(
        row_filter=IsNull("content"), selected_fields=("id",)
    ).to_arrow()
    assert_row_identifiers(deleted_rows, expected_deletions)
    changeset_rows = temporary_table.scan(
        row_filter=EqualTo("changeset", changeset.changeset_id),
        selected_fields=("id",),
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
    changeset = client.snapshot_sync(new_data, "ebsco_test")
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
            "namespace": "ebsco_test",
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
    changeset = client.snapshot_sync(new_data, "ebsco_test")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0002", "eb0003", "eb0004"}
    second_changeset = client.snapshot_sync(new_data, "ebsco_test")
    assert second_changeset is None


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
    changeset = client.snapshot_sync(new_data, "ebsco_test")
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
    newer_changeset = client.snapshot_sync(newer_data, "ebsco_test")
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
    changeset_1 = client.snapshot_sync(initial_data, "ebsco_test")
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
    changeset_2 = client.snapshot_sync(additional_data, "ebsco_test")
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
    changeset = client.snapshot_sync(new_data, "ebsco_test")
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
    changeset = client.snapshot_sync(new_data, "ebsco_test")
    assert changeset is not None
    assert set(changeset.updated_record_ids) == {"eb0002", "eb0003", "eb0004"}
    all_with_deleted = client.get_all_records(include_deleted=True)
    assert all_with_deleted.num_rows == 4
    rows = {row["id"]: row for row in all_with_deleted.to_pylist()}
    assert set(rows.keys()) == {"eb0001", "eb0002", "eb0003", "eb0004"}
    assert rows["eb0002"]["content"] is None  # deleted present
