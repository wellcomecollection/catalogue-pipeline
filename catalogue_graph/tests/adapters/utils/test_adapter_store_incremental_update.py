"""
Tests covering the incremental_update behaviour of the AdapterStore.

incremental_update performs selective updates where only the provided
records are updated or inserted. Records not present in the new data
are left unchanged (not deleted). Timestamp-based conflict resolution
ensures newer data isn't overwritten by older data.
"""

from typing import Any

import pyarrow as pa
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


def test_incremental_update_does_not_delete_missing_records(
    temporary_table: IcebergTable,
) -> None:
    """
    Given an existing table
    When incremental_update includes only a subset of records
    Then missing records are NOT deleted
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
    Given an existing table
    When incremental_update includes new records
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
    Given an existing table
    When incremental_update includes both updates and new records
    Then updates are applied and new records are inserted
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
    Given a table with data from multiple namespaces
    When incremental_update is applied to one namespace
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
    When incremental_update has a newer timestamp
    Then the record is updated
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
    When incremental_update has an older timestamp
    Then the record is NOT updated (newer data wins)
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
    When incremental_update has the same timestamp
    Then the record is NOT updated (no change needed)
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
    When incremental_update has a newer timestamp but identical content
    Then the record is NOT updated (content-based deduplication)
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
    When incremental_update has any timestamp
    Then the record is updated (null is treated as oldest)
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
    When incremental_update includes records with newer, older, and equal timestamps
    Then only records with newer timestamps and different content are updated
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
    When incremental_update includes a new record with a timestamp
    Then the record is inserted with its timestamp preserved
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
    When incremental_update has null timestamp
    Then the update is rejected (can't downgrade to null)
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
    When incremental_update is called without a last_modified column
    Then a ValueError is raised to enforce the strict requirement
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
