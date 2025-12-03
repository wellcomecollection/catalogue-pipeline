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
    unqualified_data: list[dict[str, Any]],
    namespace: str = "test_namespace",
    add_timestamp: bool = False,
) -> pa.Table:
    """
    Create an Arrow table with the repo-standard schema.

    If add_timestamp=True, include a last_modified timestamp column and preserve
    any provided last_modified values from unqualified_data; otherwise, use now (UTC).
    """
    from datetime import UTC, datetime

    data: list[dict[str, Any]] = []
    for item in unqualified_data:
        new_item = item.copy()
        new_item["namespace"] = namespace
        if add_timestamp:
            # Preserve provided last_modified or default to current time
            new_item.setdefault("last_modified", datetime.now(UTC))
        data.append(new_item)

    if add_timestamp:
        # Help mypy understand the type of fields passed to pa.schema
        fields: list[pa.Field] = [
            pa.field("namespace", pa.string(), nullable=False),
            pa.field("id", pa.string(), nullable=False),
            pa.field("content", pa.string(), nullable=True),
            pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
        ]
        return pa.Table.from_pylist(
            data,
            schema=pa.schema(fields),
        )
    else:
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
        ],
        add_timestamp=True,
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
        ],
        add_timestamp=True,
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
        ],
        add_timestamp=True,
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
        [{"id": "eb0001", "content": "ebsco updated"}],
        add_timestamp=True,
    )

    client = AdapterStore(temporary_table)
    client.incremental_update(new_ebsco_data, "test_namespace")

    # Verify axiell data is untouched
    all_records = client.get_all_records()
    rows = {(row["namespace"], row["id"]): row for row in all_records.to_pylist()}

    assert ("axiell_test", "ax0001") in rows
    assert rows[("axiell_test", "ax0001")]["content"] == "axiell data"
    assert rows[("test_namespace", "eb0001")]["content"] == "ebsco updated"


def test_incremental_update_with_newer_timestamp(temporary_table: IcebergTable) -> None:
    """
    Given an existing record with a last_modified timestamp
    When an incremental update has a newer timestamp
    Then the record should be updated
    """
    from datetime import UTC, datetime

    old_time = datetime(2025, 1, 1, 12, 0, 0, tzinfo=UTC)
    new_time = datetime(2025, 1, 2, 12, 0, 0, tzinfo=UTC)

    # Add initial record with old timestamp
    fields_initial: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    initial_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "old content",
                "last_modified": old_time,
            }
        ],
        schema=pa.schema(fields_initial),
    )
    temporary_table.append(initial_data)

    # Update with newer timestamp
    fields_update: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    new_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "new content",
                "last_modified": new_time,
            }
        ],
        schema=pa.schema(fields_update),
    )

    client = AdapterStore(temporary_table)
    result = client.incremental_update(new_data, "test_namespace")

    assert result is not None
    assert "eb0001" in result.updated_record_ids

    # Verify the content was updated
    records = temporary_table.scan().to_arrow()
    row = records.to_pylist()[0]
    assert row["content"] == "new content"
    assert row["last_modified"] == new_time


def test_incremental_update_with_older_timestamp(temporary_table: IcebergTable) -> None:
    """
    Given an existing record with a last_modified timestamp
    When an incremental update has an older timestamp
    Then the record should NOT be updated
    """
    from datetime import UTC, datetime

    new_time = datetime(2025, 1, 2, 12, 0, 0, tzinfo=UTC)
    old_time = datetime(2025, 1, 1, 12, 0, 0, tzinfo=UTC)

    # Add initial record with newer timestamp
    fields_initial_newer: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    initial_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "current content",
                "last_modified": new_time,
            }
        ],
        schema=pa.schema(fields_initial_newer),
    )
    temporary_table.append(initial_data)

    # Try to update with older timestamp
    fields_old: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    old_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "old content",
                "last_modified": old_time,
            }
        ],
        schema=pa.schema(fields_old),
    )

    client = AdapterStore(temporary_table)
    result = client.incremental_update(old_data, "test_namespace")

    # No update should occur
    assert result is None

    # Verify the content was NOT updated
    records = temporary_table.scan().to_arrow()
    row = records.to_pylist()[0]
    assert row["content"] == "current content"
    assert row["last_modified"] == new_time


def test_incremental_update_with_equal_timestamp(temporary_table: IcebergTable) -> None:
    """
    Given an existing record with a last_modified timestamp
    When an incremental update has the same timestamp
    Then the record should NOT be updated
    """
    from datetime import UTC, datetime

    same_time = datetime(2025, 1, 1, 12, 0, 0, tzinfo=UTC)

    # Add initial record
    fields_initial_same: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    initial_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "original content",
                "last_modified": same_time,
            }
        ],
        schema=pa.schema(fields_initial_same),
    )
    temporary_table.append(initial_data)

    # Try to update with same timestamp but different content
    fields_update_same: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    update_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "modified content",
                "last_modified": same_time,
            }
        ],
        schema=pa.schema(fields_update_same),
    )

    client = AdapterStore(temporary_table)
    result = client.incremental_update(update_data, "test_namespace")

    # No update should occur
    assert result is None

    # Verify the content was NOT updated
    records = temporary_table.scan().to_arrow()
    row = records.to_pylist()[0]
    assert row["content"] == "original content"


def test_incremental_update_newer_timestamp_same_content(
    temporary_table: IcebergTable,
) -> None:
    """
    Given an existing record
    When an incremental update has a newer timestamp but identical content
    Then the record should NOT be updated
    """
    from datetime import UTC, datetime, timedelta

    base_time = datetime(2025, 1, 1, 12, 0, 0, tzinfo=UTC)
    newer_time = base_time + timedelta(days=1)

    # Add initial record
    fields_initial_same_content: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    initial_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0002",
                "content": "the same content",
                "last_modified": base_time,
            }
        ],
        schema=pa.schema(fields_initial_same_content),
    )
    temporary_table.append(initial_data)

    # Attempt update with newer timestamp but identical content
    fields_update_same_content: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    update_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0002",
                "content": "the same content",
                "last_modified": newer_time,
            }
        ],
        schema=pa.schema(fields_update_same_content),
    )

    client = AdapterStore(temporary_table)
    result = client.incremental_update(update_data, "test_namespace")

    # No update should occur
    assert result is None

    # Verify the content and timestamp were NOT updated
    records = temporary_table.scan().to_arrow()
    row = records.to_pylist()[0]
    assert row["content"] == "the same content"
    assert row["last_modified"] == base_time


def test_incremental_update_with_null_existing_timestamp(
    temporary_table: IcebergTable,
) -> None:
    """
    Given an existing record with null last_modified (legacy data)
    When an incremental update has any timestamp
    Then the record should be updated
    """
    from datetime import UTC, datetime

    # Add initial record without timestamp (legacy data)
    initial_data = data_to_namespaced_table(
        [{"id": "eb0001", "content": "legacy content"}]
    )
    temporary_table.append(initial_data)

    # Update with a timestamp
    new_time = datetime(2025, 1, 1, 12, 0, 0, tzinfo=UTC)
    fields_with_timestamp: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    new_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "updated content",
                "last_modified": new_time,
            }
        ],
        schema=pa.schema(fields_with_timestamp),
    )

    client = AdapterStore(temporary_table)
    result = client.incremental_update(new_data, "test_namespace")

    assert result is not None
    assert "eb0001" in result.updated_record_ids

    # Verify the content was updated
    records = temporary_table.scan().to_arrow()
    row = records.to_pylist()[0]
    assert row["content"] == "updated content"
    assert row["last_modified"] == new_time


def test_incremental_update_mixed_timestamps(temporary_table: IcebergTable) -> None:
    """
    Given multiple existing records with various timestamps
    When an incremental update includes records with newer, older, and equal timestamps
    Then only records with newer timestamps should be updated
    """
    from datetime import UTC, datetime

    time_old = datetime(2025, 1, 1, 12, 0, 0, tzinfo=UTC)
    time_current = datetime(2025, 1, 2, 12, 0, 0, tzinfo=UTC)
    time_new = datetime(2025, 1, 3, 12, 0, 0, tzinfo=UTC)

    # Add initial records with various timestamps
    fields_mixed_initial: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    initial_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "record 1 old",
                "last_modified": time_old,
            },
            {
                "namespace": "test_namespace",
                "id": "eb0002",
                "content": "record 2 current",
                "last_modified": time_current,
            },
            {
                "namespace": "test_namespace",
                "id": "eb0003",
                "content": "record 3 current",
                "last_modified": time_current,
            },
            {
                "namespace": "test_namespace",
                "id": "eb0004",
                "content": "record 4 legacy",
                "last_modified": None,
            },
        ],
        schema=pa.schema(fields_mixed_initial),
    )
    temporary_table.append(initial_data)

    # Update with mixed timestamps
    fields_mixed_update: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    update_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "record 1 NEW",  # Newer timestamp - SHOULD update
                "last_modified": time_current,
            },
            {
                "namespace": "test_namespace",
                "id": "eb0002",
                "content": "record 2 OLD",  # Older timestamp - should NOT update
                "last_modified": time_old,
            },
            {
                "namespace": "test_namespace",
                "id": "eb0003",
                "content": "record 3 SAME",  # Same timestamp - should NOT update
                "last_modified": time_current,
            },
            {
                "namespace": "test_namespace",
                "id": "eb0004",
                "content": "record 4 NEW",  # Null existing - SHOULD update
                "last_modified": time_new,
            },
        ],
        schema=pa.schema(fields_mixed_update),
    )

    client = AdapterStore(temporary_table)
    result = client.incremental_update(update_data, "test_namespace")

    assert result is not None
    # Only eb0001 and eb0004 should be updated
    assert set(result.updated_record_ids) == {"eb0001", "eb0004"}

    # Verify the correct records were updated
    records = temporary_table.scan().to_arrow().sort_by("id")
    rows = {row["id"]: row for row in records.to_pylist()}

    assert rows["eb0001"]["content"] == "record 1 NEW"
    assert rows["eb0001"]["last_modified"] == time_current

    assert rows["eb0002"]["content"] == "record 2 current"  # NOT updated
    assert rows["eb0002"]["last_modified"] == time_current

    assert rows["eb0003"]["content"] == "record 3 current"  # NOT updated
    assert rows["eb0003"]["last_modified"] == time_current

    assert rows["eb0004"]["content"] == "record 4 NEW"
    assert rows["eb0004"]["last_modified"] == time_new


def test_incremental_update_with_new_record_with_timestamp(
    temporary_table: IcebergTable,
) -> None:
    """
    Given an empty table
    When an incremental update includes a new record with a timestamp
    Then the record should be inserted with its timestamp preserved
    """
    from datetime import UTC, datetime

    new_time = datetime(2025, 1, 1, 12, 0, 0, tzinfo=UTC)

    # Insert new record with timestamp
    fields_new_record_timestamp: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    new_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "new record",
                "last_modified": new_time,
            }
        ],
        schema=pa.schema(fields_new_record_timestamp),
    )

    client = AdapterStore(temporary_table)
    result = client.incremental_update(new_data, "test_namespace")

    assert result is not None
    assert "eb0001" in result.updated_record_ids

    # Verify the record was inserted with timestamp
    records = temporary_table.scan().to_arrow()
    row = records.to_pylist()[0]
    assert row["content"] == "new record"
    assert row["last_modified"] == new_time


def test_incremental_update_null_timestamp_on_timestamped_record(
    temporary_table: IcebergTable,
) -> None:
    """
    Given an existing record with a last_modified timestamp
    When an incremental update has null timestamp
    Then the update should be rejected
    """
    from datetime import UTC, datetime

    existing_time = datetime(2025, 1, 1, 12, 0, 0, tzinfo=UTC)

    # Add initial record with timestamp
    fields_timestamped_initial: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    initial_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "timestamped content",
                "last_modified": existing_time,
            }
        ],
        schema=pa.schema(fields_timestamped_initial),
    )
    temporary_table.append(initial_data)

    # Try to update with null timestamp
    fields_null_timestamp_update: list[pa.Field] = [
        pa.field("namespace", pa.string(), nullable=False),
        pa.field("id", pa.string(), nullable=False),
        pa.field("content", pa.string(), nullable=True),
        pa.field("last_modified", pa.timestamp("us", "UTC"), nullable=True),
    ]
    update_data = pa.Table.from_pylist(
        [
            {
                "namespace": "test_namespace",
                "id": "eb0001",
                "content": "new content without timestamp",
                "last_modified": None,
            }
        ],
        schema=pa.schema(fields_null_timestamp_update),
    )

    client = AdapterStore(temporary_table)
    result = client.incremental_update(update_data, "test_namespace")

    # No update should occur
    assert result is None

    # Verify the content was NOT updated
    records = temporary_table.scan().to_arrow()
    row = records.to_pylist()[0]
    assert row["content"] == "timestamped content"
    assert row["last_modified"] == existing_time


def test_incremental_update_requires_last_modified_column(
    temporary_table: IcebergTable,
) -> None:
    """
    Given incremental_update is called without a last_modified column
    Then a ValueError should be raised to enforce the strict requirement.
    """
    # Initial data
    temporary_table.append(
        data_to_namespaced_table(
            [
                {"id": "eb0001", "content": "hello"},
            ]
        )
    )

    # Incremental update without last_modified column
    new_data = data_to_namespaced_table(
        [
            {"id": "eb0001", "content": "updated"},
        ]
    )

    client = AdapterStore(temporary_table)
    import pytest

    with pytest.raises(ValueError):
        client.incremental_update(new_data, "test_namespace")
