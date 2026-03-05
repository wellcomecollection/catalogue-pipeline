import uuid
from abc import ABC, abstractmethod
from typing import cast

import pyarrow as pa
import pyarrow.compute as pc
from pydantic import BaseModel
from pyiceberg.expressions import BooleanExpression, EqualTo, In
from pyiceberg.schema import Schema
from pyiceberg.table import Table as IcebergTable
from pyiceberg.table.upsert_util import get_rows_to_update

from adapters.utils.schemata import CHANGESET_FIELD, LAST_MODIFIED_FIELD


class AdapterStoreUpdate(BaseModel):
    changeset_id: str
    updated_record_ids: list[str]


class PipelineStore(ABC):
    """
    Encapsulates operations on a single Iceberg table.

    Optionally accepts a default_namespace which will be used in update()
    if a namespace isn't provided at call time.
    """

    def __init__(self, table: IcebergTable, namespace: str):
        self.table = table
        self.namespace = namespace

    @property
    @abstractmethod
    def schema(self) -> Schema:
        pass

    def _cast_to_arrow_schema(self, new_data: pa.Table, operation: str) -> pa.Table:
        """Ensure the provided Arrow table matches the repo-standard adapter schema.

        We cast inputs for consistency across adapters and to fail fast when an adapter
        produces a table that can't be safely written to the Iceberg table.
        """
        try:
            return new_data.cast(self.schema)
        # pyarrow.Table.cast can raise either a pyarrow ArrowException subclass
        # or a plain ValueError (e.g. mismatched field names).
        except (pa.ArrowException, ValueError) as e:
            raise ValueError(
                f"{operation} requires new_data to be castable to the required schema; "
                f"got schema: {new_data.schema}"
            ) from e

    def _get_existing_data(self, row_filter: BooleanExpression) -> pa.Table:
        return self.table.scan(row_filter=row_filter).to_arrow().cast(self.schema)

    def _upsert_with_markers(
        self,
        changes: pa.Table | None,
        inserts: pa.Table | None,
        timestamp: pa.Scalar | None = None,
    ) -> AdapterStoreUpdate:
        """
        Insert and update records, adding the timestamp and changeset values to
        any changed rows.
        :param changes: New versions of existing records to change
        :param inserts: New records to insert
        """
        changeset_id = str(uuid.uuid1())

        if changes is not None:
            changes = self._set_change_columns(changes, changeset_id, timestamp)
        if inserts is not None:
            inserts = self._set_change_columns(inserts, changeset_id, timestamp)
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
    def _set_change_columns(
        changeset: pa.Table, changeset_id: str, timestamp: pa.Scalar | None = None
    ) -> pa.Table:
        # Replace changeset column with the new changeset_id
        idx = changeset.schema.get_field_index("changeset")
        changeset = changeset.set_column(
            idx, CHANGESET_FIELD, pa.repeat(changeset_id, changeset.num_rows)
        )

        # Replace last_modified column if timestamp provided
        if timestamp is not None:
            idx = changeset.schema.get_field_index("last_modified")
            changeset = changeset.set_column(
                idx, LAST_MODIFIED_FIELD, pa.repeat(timestamp, changeset.num_rows)
            )

        return changeset

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
    def _find_updates(existing_data: pa.Table, new_data: pa.Table) -> pa.Table:
        # We only consider content changes as a reason to update.
        # Timestamps are used as a gate (must be newer) but should not themselves
        # trigger an update if the content is identical.
        compare_cols = ["namespace", "id", "content"]
        join_fields = ["namespace", "id"]

        # Handle schema mismatch (e.g. extra columns in new_data)
        common_cols = [c for c in compare_cols if c in new_data.column_names]
        if not common_cols:
            # No comparable columns; nothing to update
            return new_data.slice(0, 0)

        new_projected = new_data.select(common_cols)
        existing_projected = existing_data.select(common_cols)
        updates_projected = get_rows_to_update(
            new_projected, existing_projected, join_fields
        )

        update_ids = updates_projected.column("id")
        return new_data.filter(pc.field("id").isin(update_ids))

    @staticmethod
    def _filter_by_timestamp(updates: pa.Table, existing_data: pa.Table) -> pa.Table:
        """
        Filter updates to only include records where the new last_modified is newer than
        the existing last_modified.

        Records are included if:
        - The new record has a last_modified that is strictly greater than the existing one

        Args:
            updates: Table of candidate updates from _find_updates (with last_modified)
            existing_data: Table of existing records (with last_modified)

        Returns:
            Filtered table containing only updates that should be applied
        """
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

            if new_timestamp > existing_timestamp:
                rows_to_keep.append(i)

        # Use PyArrow's take to efficiently select rows by index
        return updates.take(rows_to_keep)

    def get_records_by_changeset(self, changeset_id: str) -> pa.Table:
        return self.table.scan(row_filter=EqualTo("changeset", changeset_id)).to_arrow()
