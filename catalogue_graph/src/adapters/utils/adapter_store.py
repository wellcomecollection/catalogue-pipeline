from datetime import UTC, datetime

import pyarrow as pa
import pyarrow.compute as pc
from pydantic import BaseModel
from pyiceberg.expressions import And, EqualTo, In, IsNull, Or
from pyiceberg.table import Table as IcebergTable

from adapters.utils.pipeline_store import PipelineStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA


class AdapterStoreUpdate(BaseModel):
    changeset_id: str
    updated_record_ids: list[str]


class AdapterStore(PipelineStore):
    """
    Encapsulates operations on a single Iceberg table.

    Optionally accepts a default_namespace which will be used in update()
    if a namespace isn't provided at call time.
    """

    def __init__(self, table: IcebergTable, namespace: str):
        super().__init__(table, namespace)

    @property
    def schema(self):
        return ADAPTER_STORE_ARROW_SCHEMA

    def incremental_update(self, new_data: pa.Table) -> AdapterStoreUpdate | None:
        """
        Apply an incremental update to the table.

        This will insert new records and update changed records.
        Records with unchanged content will be left alone.
        """
        new_data = self._cast_to_arrow_schema(new_data, operation="incremental_update")

        if new_data.num_rows == 0:
            return None

        # Enforce presence of timestamps in incremental updates to avoid overwriting
        # newer records with untimed data.
        if pc.any(pc.is_null(new_data.column("last_modified"))).as_py():
            raise ValueError(
                "incremental_update requires a non-Null 'last_modified' in new_data"
            )

        # Fetch rows that match the incoming IDs
        incoming_ids = [
            val for val in new_data.column("id").to_pylist() if val is not None
        ]
        row_filter = And(EqualTo("namespace", self.namespace), In("id", incoming_ids))
        existing_data = self._get_existing_data(row_filter)

        if existing_data.num_rows > 0:
            existing_data = existing_data.sort_by("id")
            new_data = new_data.sort_by("id")

            updates = self._find_updates(existing_data, new_data)
            # Filter updates to only include records with newer timestamps
            updates = self._filter_by_timestamp(updates, existing_data)
            # Preserve content for records being marked as deleted
            updates = self._preserve_content_for_deletions(updates, existing_data)
            inserts = self._find_inserts(existing_data, new_data, self.namespace)
            changes = updates
        else:
            inserts = new_data
            changes = None

        if changes or inserts:
            return self._upsert_with_markers(changes, inserts)

        return None

    def snapshot_sync(self, new_data: pa.Table) -> AdapterStoreUpdate | None:
        """
        Sync the table to match the new snapshot.

        This will insert new records, update changed records, and soft-delete
        records that are no longer present in the snapshot by setting their content to null.
        """
        new_data = self._cast_to_arrow_schema(new_data, operation="snapshot_sync")

        row_filter = EqualTo("namespace", self.namespace)
        existing_data = self._get_existing_data(row_filter)

        if existing_data.num_rows > 0:
            existing_data = existing_data.sort_by("id")
            new_data = new_data.sort_by("id")

            deletes = self._find_snapshot_deletes(
                existing_data, new_data, self.namespace
            )
            updates = self._find_updates(existing_data, new_data)
            inserts = self._find_inserts(existing_data, new_data, self.namespace)

            changes = pa.concat_tables([deletes, updates]) if deletes else updates
        else:
            inserts = new_data
            changes = None

        if changes or inserts:
            # replace last_modified timestamps with current time for snapshot sync
            timestamp = pa.scalar(datetime.now(UTC), pa.timestamp("us", "UTC"))
            return self._upsert_with_markers(changes, inserts, timestamp=timestamp)

        return None

    def get_all_records(self, include_deleted: bool = False) -> pa.Table:
        """Return all records in the table.

        By default, rows marked as deleted are excluded.

        During a full reindex we are writing into an empty index,
        so no need to include deleted rows to overwrite documents.

        Set include_deleted=True to return them as well.
        """
        if include_deleted:
            return self.table.scan().to_arrow()

        return self.table.scan(
            row_filter=Or(EqualTo("deleted", False), IsNull("deleted"))
        ).to_arrow()

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
