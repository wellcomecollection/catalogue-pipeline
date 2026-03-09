from datetime import UTC, datetime

import pyarrow as pa
import pyarrow.compute as pc
from pyiceberg.expressions import EqualTo, IsNull, Or
from pyiceberg.table import Table as IcebergTable

from adapters.utils.pipeline_store import PipelineStore, PipelineStoreUpdate
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA


class AdapterStore(PipelineStore):
    """Store for adapter content records with incremental updates and snapshot sync."""

    def __init__(self, table: IcebergTable, namespace: str):
        super().__init__(table, namespace)

    @property
    def schema(self) -> pa.Schema:
        return ADAPTER_STORE_ARROW_SCHEMA

    @property
    def _compare_columns(self) -> list[str]:
        return ["content"]

    def _transform_incremental_updates(
        self, updates: pa.Table, existing_data: pa.Table
    ) -> pa.Table:
        return self._preserve_content_for_deletions(updates, existing_data)

    def snapshot_sync(self, new_data: pa.Table) -> PipelineStoreUpdate | None:
        """
        Sync the table to match the new snapshot.

        This will insert new records, update changed records, and soft-delete
        records that are no longer present in the snapshot by setting their content to null.
        """
        # replace last_modified timestamps with current time for snapshot sync
        now_timestamp = datetime.now(UTC)
        new_data = self._set_last_modified(new_data, now_timestamp)
        new_data = self._cast_to_arrow_schema(new_data, operation="snapshot_sync")

        existing_data = self.get_records_in_namespace()

        if existing_data.num_rows > 0:
            existing_data = existing_data.sort_by("id")
            new_data = new_data.sort_by("id")

            deletes = self._find_snapshot_deletes(
                existing_data, new_data, self.namespace
            )
            updates = self._find_updates(existing_data, new_data)
            inserts = self._find_inserts(existing_data, new_data)

            changes = pa.concat_tables([deletes, updates]) if deletes else updates
        else:
            inserts = new_data
            changes = None

        if changes:
            changes = self._set_last_modified(changes, now_timestamp)
        if inserts:
            inserts = self._set_last_modified(inserts, now_timestamp)

        return self._upsert_with_markers(changes, inserts)

    def get_active_records_in_namespace(self) -> pa.Table:
        """Return non-deleted records in the store namespace."""
        non_deleted_filter = Or(EqualTo("deleted", False), IsNull("deleted"))
        return self.get_records_in_namespace(non_deleted_filter)

    @staticmethod
    def _preserve_content_for_deletions(
        updates: pa.Table, existing_data: pa.Table
    ) -> pa.Table:
        """
        Preserve existing content for records being marked as deleted.

        When a record is marked as deleted (deleted=True) with content=None,
        replace the null content with the existing content from the table.
        This ensures we can replay deletions downstream if needed.

        Args:
            updates: Table of updates to apply (may include deletions)
            existing_data: Table of existing records

        Returns:
            Updated table with preserved content for deletions
        """
        if updates.num_rows == 0:
            return updates

        # Build lookup of existing content by id
        existing_content = {
            row["id"]: row["content"] for row in existing_data.to_pylist()
        }

        # Check if any records need content preservation
        rows = updates.to_pylist()
        needs_update = False
        for row in rows:
            if row.get("deleted") is True and row.get("content") is None:
                existing = existing_content.get(row["id"])
                if existing is not None:
                    row["content"] = existing
                    needs_update = True

        if not needs_update:
            return updates

        # Rebuild table with preserved content
        return pa.Table.from_pylist(rows, schema=updates.schema)

    @staticmethod
    def _find_snapshot_deletes(
        existing_data: pa.Table, new_data: pa.Table, record_namespace: str
    ) -> pa.Table:
        """
        Find records in `existing_data` that are not in `new_data`, and produce a
        pyarrow Table that can be used to update those records by marking them as deleted.
        """
        new_ids = new_data.column("id")
        # Check for records that are not already deleted (deleted is null or False)
        not_deleted = pc.field("deleted").is_null() | (pc.field("deleted") == False)  # noqa: E712
        missing_ids = existing_data.filter(
            ~pc.field("id").isin(new_ids)
            & (pc.field("namespace") == record_namespace)
            & not_deleted
        )

        num_rows = missing_ids.num_rows
        deleted_array = pa.array([True] * num_rows, type=pa.bool_())

        return missing_ids.set_column(
            missing_ids.schema.get_field_index("deleted"), "deleted", deleted_array
        )
