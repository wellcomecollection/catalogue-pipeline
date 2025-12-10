import uuid
from datetime import UTC, datetime
from typing import cast

import pyarrow as pa
import pyarrow.compute as pc
from pydantic import BaseModel
from pyiceberg.expressions import And, BooleanExpression, EqualTo, In, IsNull, Not
from pyiceberg.table import Table as IcebergTable
from pyiceberg.table.upsert_util import get_rows_to_update

from adapters.utils.schemata import ARROW_SCHEMA, ARROW_SCHEMA_WITH_TIMESTAMP


class AdapterStoreUpdate(BaseModel):
    changeset_id: str
    updated_record_ids: list[str]


class AdapterStore:
    """
    Encapsulates operations on a single Iceberg table.

    Optionally accepts a default_namespace which will be used in update()
    if a namespace isn't provided at call time.
    """

    def __init__(self, table: IcebergTable, default_namespace: str | None = None):
        self.table = table
        self.default_namespace = default_namespace

    def incremental_update(
        self, new_data: pa.Table, record_namespace: str | None = None
    ) -> AdapterStoreUpdate | None:
        """
        Apply an incremental update to the table.
        Only updates and inserts are processed; missing records are NOT deleted.
        """

        namespace = self._get_namespace(record_namespace)
        if new_data.num_rows == 0:
            return None

        # Enforce presence of timestamps in incremental updates to avoid overwriting
        # newer records with untimed data.
        if "last_modified" not in new_data.column_names:
            raise ValueError(
                "incremental_update requires a 'last_modified' column in new_data"
            )

        # Optimization: For incremental updates, only fetch rows that match the incoming IDs.
        new_ids = [val for val in new_data.column("id").to_pylist() if val is not None]

        # If there are no IDs, there's nothing to update (and inserts would fail if ID is required, but let's assume valid data)
        if not new_ids:
            return None

        row_filter = And(EqualTo("namespace", namespace), In("id", new_ids))
        # Fetch existing data with timestamps for comparison (timestamps required)
        existing_data = self._get_existing_data(row_filter, include_timestamp=True)

        if existing_data.num_rows > 0:
            existing_data = existing_data.sort_by("id")
            new_data = new_data.sort_by("id")

            updates = self._find_updates(existing_data, new_data)
            # Filter updates to only include records with newer timestamps
            updates = self._filter_by_timestamp(updates, existing_data)
            inserts = self._find_inserts(existing_data, new_data, namespace)
            changes = updates
        else:
            inserts = new_data
            changes = None

        if changes or inserts:
            return self._upsert_with_markers(changes, inserts)
        return None

    def snapshot_sync(
        self, new_data: pa.Table, record_namespace: str | None = None
    ) -> str | None:
        """
        Sync the table to match the new snapshot.
        Updates, inserts, and DELETES records that are missing from new_data.
        """
        namespace = self._get_namespace(record_namespace)

        # Optimization: Always filter by namespace
        row_filter = EqualTo("namespace", namespace)
        existing_data = self._get_existing_data(row_filter)

        if existing_data.num_rows > 0:
            existing_data = existing_data.sort_by("id")
            new_data = new_data.sort_by("id")

            deletes = self._get_deletes(existing_data, new_data, namespace)
            updates = self._find_updates(existing_data, new_data)
            inserts = self._find_inserts(existing_data, new_data, namespace)

            changes = pa.concat_tables([deletes, updates]) if deletes else updates
        else:
            inserts = new_data
            changes = None

        if changes or inserts:
            return self._upsert_with_markers(changes, inserts).changeset_id
        return None

    def _get_namespace(self, record_namespace: str | None) -> str:
        namespace = record_namespace or self.default_namespace
        if namespace is None:
            raise ValueError(
                "record_namespace must be supplied, or default_namespace must be set on the updater"
            )
        return namespace

    def _get_existing_data(
        self, row_filter: BooleanExpression, include_timestamp: bool = False
    ) -> pa.Table:
        selected_fields: tuple[str, ...] = ("namespace", "id", "content")
        schema = ARROW_SCHEMA

        if include_timestamp:
            selected_fields = ("namespace", "id", "content", "last_modified")
            schema = ARROW_SCHEMA_WITH_TIMESTAMP

        return (
            self.table.scan(
                selected_fields=selected_fields,
                row_filter=row_filter,
            )
            .to_arrow()
            .cast(schema)
        )

    def get_records_by_changeset(self, changeset_id: str) -> pa.Table:
        return self.table.scan(row_filter=EqualTo("changeset", changeset_id)).to_arrow()

    def get_all_records(self, include_deleted: bool = False) -> pa.Table:
        """Return all records in the table.

        By default, rows whose content field is null (i.e. soft-deleted) are excluded.

        During a full reindex we are writing into an empty index,
        so no need to include deleted rows to overwrite documents.

        Set include_deleted=True to return them as well.
        """
        if include_deleted:
            return self.table.scan().to_arrow()
        return self.table.scan(row_filter=Not(IsNull("content"))).to_arrow()

    def _upsert_with_markers(
        self, changes: pa.Table | None, inserts: pa.Table | None
    ) -> AdapterStoreUpdate:
        """
        Insert and update records, adding the timestamp and changeset values to
        any changed rows.
        :param changes: New versions of existing records to change
        :param inserts: New records to insert
        """
        changeset_id = str(uuid.uuid1())
        timestamp = pa.scalar(datetime.now(UTC), pa.timestamp("us", "UTC"))
        if changes is not None:
            changes = self._append_change_columns(changes, changeset_id, timestamp)
        if inserts is not None:
            inserts = self._append_change_columns(inserts, changeset_id, timestamp)
        with self.table.transaction() as tx:
            # Because we already know which records to overwrite and which ones to append,
            # we can avoid all the extra processing that happens inside table.upsert to find
            # matching records, check them for differences etc.
            # Just overwrite all the `changes` and append all the `inserts`
            if changes is not None:
                overwrite_mask_predicate = self._create_match_filter(changes)
                tx.overwrite(changes, overwrite_filter=overwrite_mask_predicate)
            if inserts is not None:
                tx.append(inserts)

        updated_ids: list[str] = []
        for t in (changes, inserts):
            if t is not None:
                updated_ids.extend(cast(list[str], t.column("id").to_pylist()))

        return AdapterStoreUpdate(
            changeset_id=changeset_id, updated_record_ids=updated_ids
        )

    @staticmethod
    def _create_match_filter(changes: pa.Table) -> BooleanExpression:
        # to_pylist returns list[Any | None]; Iceberg In expects a concrete literal type.
        raw_ids = changes.column("id").to_pylist()
        change_ids = cast(list[str], [i for i in raw_ids if isinstance(i, str)])
        return In("id", change_ids)

    @staticmethod
    def _append_change_columns(
        changeset: pa.Table, changeset_id: str, timestamp: pa.Scalar
    ) -> pa.Table:
        # Build correctly-typed Arrow arrays for the metadata columns we're appending.
        num_rows = changeset.num_rows
        changeset_array = pa.array([changeset_id] * num_rows, type=pa.string())

        changeset = changeset.append_column(
            pa.field("changeset", type=pa.string(), nullable=True),
            changeset_array,
        )

        if "last_modified" not in changeset.column_names:
            # Convert the Arrow scalar to a Python datetime so the array constructor can repeat it
            last_modified_py = timestamp.as_py()
            last_modified_array = pa.array(
                [last_modified_py] * num_rows,
                type=pa.timestamp("us", "UTC"),
            )

            changeset = changeset.append_column(
                pa.field(
                    "last_modified", type=pa.timestamp("us", "UTC"), nullable=True
                ),
                last_modified_array,
            )

        return changeset

    @staticmethod
    def _find_updates(existing_data: pa.Table, new_data: pa.Table) -> pa.Table:
        # We only consider content changes as a reason to update.
        # Timestamps are used as a gate (must be newer) but should not themselves
        # trigger an update if the content is identical.
        compare_cols = ["namespace", "id", "content"]

        if set(existing_data.column_names) == set(new_data.column_names):
            if all(col in new_data.column_names for col in compare_cols):
                updates_projected = get_rows_to_update(
                    new_data.select(compare_cols),
                    existing_data.select(compare_cols),
                    ["namespace", "id"],
                )
                if len(updates_projected) == 0:
                    return new_data.slice(0, 0)
                update_ids = updates_projected.column("id")
                return new_data.filter(pc.field("id").isin(update_ids))
            # Fallback to original behaviour if content column missing
            return get_rows_to_update(new_data, existing_data, ["namespace", "id"])

        # Handle schema mismatch (e.g. extra columns in new_data)
        common_cols = [c for c in compare_cols if c in new_data.column_names]
        if not common_cols:
            # No comparable columns; nothing to update
            return new_data.slice(0, 0)

        new_projected = new_data.select(common_cols)
        existing_projected = existing_data.select(common_cols)

        updates_projected = get_rows_to_update(
            new_projected, existing_projected, ["namespace", "id"]
        )

        if len(updates_projected) == 0:
            return new_data.slice(0, 0)

        update_ids = updates_projected.column("id")
        return new_data.filter(pc.field("id").isin(update_ids))

    @staticmethod
    def _find_inserts(
        existing_data: pa.Table, new_data: pa.Table, record_namespace: str
    ) -> pa.Table:
        old_ids = existing_data.column("id")
        missing_records = new_data.filter(
            (pc.field("namespace") == record_namespace) & ~pc.field("id").isin(old_ids)
        )
        return missing_records

    @staticmethod
    def _filter_by_timestamp(updates: pa.Table, existing_data: pa.Table) -> pa.Table:
        """
        Filter updates to only include records where the new last_modified is newer than
        the existing last_modified.

        Records are included if:
        - The existing record has null last_modified (legacy data, always update)
        - The new record has a last_modified that is strictly greater than the existing one

        Records are excluded if:
        - New last_modified is null but existing has a timestamp (reject untimed updates)
        - New last_modified <= existing last_modified (don't overwrite newer with older)

        Args:
            updates: Table of candidate updates from _find_updates (with last_modified)
            existing_data: Table of existing records (with last_modified)

        Returns:
            Filtered table containing only updates that should be applied
        """
        if updates.num_rows == 0:
            return updates

        # If updates don't have last_modified column, return as-is (backward compatibility)
        if "last_modified" not in updates.column_names:
            return updates

        # Create a lookup dictionary for existing timestamps
        existing_timestamps = {
            row["id"]: row["last_modified"] for row in existing_data.to_pylist()
        }

        # Filter updates to only include those with newer timestamps
        rows_to_keep = []
        for i in range(updates.num_rows):
            update_row = updates.slice(i, 1).to_pylist()[0]
            record_id = update_row["id"]
            new_timestamp = update_row.get("last_modified")
            existing_timestamp = existing_timestamps.get(record_id)

            # Keep the update if:
            # 1. Existing timestamp is None (legacy data or newly inserted)
            # 2. New timestamp is not None and is greater than existing timestamp
            #
            # Reject the update if:
            # 3. New timestamp is None but existing has a timestamp (reject updates without timestamps)
            # 4. New timestamp <= existing timestamp (don't overwrite newer with older)
            if existing_timestamp is None:
                rows_to_keep.append(i)
            elif new_timestamp is None:
                # Reject: new record has no timestamp but existing does
                # Don't accept data without timestamps that could overwrite timestamped data
                pass
            elif new_timestamp > existing_timestamp:
                rows_to_keep.append(i)

        if not rows_to_keep:
            # Return empty table with same schema
            return updates.slice(0, 0)

        # Use PyArrow's take to efficiently select rows by index
        return updates.take(rows_to_keep)

    @staticmethod
    def _get_deletes(
        existing_data: pa.Table, new_data: pa.Table, record_namespace: str
    ) -> pa.Table:
        """
        Find records in `existing_data` that are not in `new_data`, and produce a
        pyarrow Table that can be used to update those records by emptying their content.
        """
        new_ids = new_data.column("id")
        missing_ids = existing_data.filter(
            # records that have already been "deleted" do not need to be deleted again.
            (~pc.field("content").is_null())
            & (pc.field("namespace") == record_namespace)
            & ~pc.field("id").isin(new_ids)
        ).column("id")
        return pa.Table.from_pylist(
            [{"namespace": record_namespace, "id": id.as_py()} for id in missing_ids],
            schema=ARROW_SCHEMA,
        )
