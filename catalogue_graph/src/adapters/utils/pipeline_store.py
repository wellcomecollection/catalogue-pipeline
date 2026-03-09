import uuid
from abc import ABC, abstractmethod
from datetime import datetime
from typing import Any, cast

import pyarrow as pa
import pyarrow.compute as pc
from pydantic import BaseModel
from pyiceberg.expressions import And, BooleanExpression, EqualTo, In
from pyiceberg.table import ALWAYS_TRUE
from pyiceberg.table import Table as IcebergTable
from pyiceberg.table.upsert_util import get_rows_to_update


class PipelineStoreUpdate(BaseModel):
    changeset_id: str
    updated_record_ids: list[str]


class PipelineStore(ABC):
    """Base class for namespaced Iceberg tables.

    Expects a schema that includes `id`, `namespace`, `last_modified`, and `changeset` fields.
    Provides schema casting, namespace filtering, update selection helpers, and
    changeset tagging. Subclasses define the concrete schema and public update flows.
    """

    def __init__(self, table: IcebergTable, namespace: str):
        self.table = table
        self.namespace = namespace

    @property
    @abstractmethod
    def schema(self) -> pa.Schema:
        """Return the Arrow schema for rows in this table."""

    @property
    @abstractmethod
    def _compare_columns(self) -> list[str]:
        """Return columns used to detect content changes for incremental updates."""

    def get_all_records(self) -> pa.Table:
        """Return all records in the table from all namespaces."""
        return self.table.scan().to_arrow().cast(self.schema)

    def get_records_in_namespace(
        self, iceberg_filter: BooleanExpression = ALWAYS_TRUE
    ) -> pa.Table:
        """Return records in the store namespace, optionally filtered."""
        full_filter = And(EqualTo("namespace", self.namespace), iceberg_filter)
        return self.table.scan(row_filter=full_filter).to_arrow().cast(self.schema)

    def _cast_to_arrow_schema(self, new_data: pa.Table, operation: str) -> pa.Table:
        """Ensure the provided Arrow table matches the expected schema.

        We cast inputs for consistency across adapters and to fail fast when an adapter
        produces a table that can't be safely written to the Iceberg table.
        """
        try:
            return new_data.cast(self.schema)
        # pyarrow.Table.cast can raise either a pyarrow ArrowException subclass
        # or a plain ValueError (e.g. mismatched field names).
        except (pa.ArrowException, ValueError) as e:
            raise ValueError(
                f"{operation} requires new_data to be castable to schema: {self.schema}; "
                f"got schema: {new_data.schema}"
            ) from e

    def _transform_incremental_updates(
        self, updates: pa.Table, existing_data: pa.Table
    ) -> pa.Table:
        """Apply store-specific update transformations before writing."""
        return updates

    def incremental_update(self, new_data: pa.Table) -> PipelineStoreUpdate | None:
        """Apply an incremental update for incoming rows.

        Casts input to the store schema, loads existing rows for the incoming IDs,
        selects updates based on the compare columns and newer timestamps, applies any
        store-specific update transformations, and appends inserts. Returns a changeset
        summary or None if no rows are provided.
        """
        new_data = self._cast_to_arrow_schema(new_data, operation="incremental_update")

        if new_data.num_rows == 0:
            return None

        incoming_ids = cast(list[str], new_data.column("id").to_pylist())
        existing_data = self.get_records_in_namespace(In("id", incoming_ids))

        if existing_data.num_rows > 0:
            updates = self._find_updates(existing_data, new_data)
            updates = self._filter_by_timestamp(updates, existing_data)
            updates = self._transform_incremental_updates(updates, existing_data)
            inserts = self._find_inserts(existing_data, new_data)
            changes = updates
        else:
            inserts = new_data
            changes = None

        return self._upsert_with_markers(changes, inserts)

    def _upsert_with_markers(
        self, changes: pa.Table | None, inserts: pa.Table | None
    ) -> PipelineStoreUpdate | None:
        """Overwrite changes and append inserts, tagging rows with a changeset ID."""
        if not (changes or inserts):
            return None

        changeset_id = str(uuid.uuid1())
        with self.table.transaction() as tx:
            # Because we already know which records to overwrite and which ones to append,
            # we can avoid all the extra processing that happens inside table.upsert to find
            # matching records, check them for differences etc.
            # Just overwrite all the `changes` and append all the `inserts`
            if changes is not None:
                changes = self._set_changeset_id(changes, changeset_id)
                overwrite_mask_predicate = self._create_match_filter(changes)
                tx.overwrite(changes, overwrite_filter=overwrite_mask_predicate)
            if inserts is not None:
                inserts = self._set_changeset_id(inserts, changeset_id)
                tx.append(inserts)

        updated_ids: list[str] = []
        for t in (changes, inserts):
            if t is not None:
                updated_ids.extend(cast(list[str], t.column("id").to_pylist()))

        return PipelineStoreUpdate(
            changeset_id=changeset_id, updated_record_ids=updated_ids
        )

    @staticmethod
    def _create_match_filter(changes: pa.Table) -> BooleanExpression:
        # to_pylist returns list[Any | None]; Iceberg In expects a concrete literal type.
        raw_ids = changes.column("id").to_pylist()
        change_ids = cast(list[str], [i for i in raw_ids if isinstance(i, str)])
        return In("id", change_ids)

    def _set_value(self, changeset: pa.Table, field_name: str, value: Any) -> pa.Table:
        field_index = changeset.schema.get_field_index(field_name)
        field = self.schema.field_by_name(field_name)
        values = pa.repeat(value, changeset.num_rows).cast(field.type)
        return changeset.set_column(field_index, field, values)

    def _set_last_modified(self, changeset: pa.Table, timestamp: datetime) -> pa.Table:
        return self._set_value(changeset, "last_modified", timestamp)

    def _set_changeset_id(self, changeset: pa.Table, changeset_id: str) -> pa.Table:
        return self._set_value(changeset, "changeset", changeset_id)

    def _find_inserts(self, existing_data: pa.Table, new_data: pa.Table) -> pa.Table:
        namespace_filter = pc.field("namespace") == self.namespace
        existing_ids_filter = pc.field("id").isin(existing_data.column("id"))

        # Filter for rows which are in the correct namespace and whose IDs do NOT exist in the existing table
        return new_data.filter(namespace_filter & ~existing_ids_filter)

    def _find_updates(self, existing_data: pa.Table, new_data: pa.Table) -> pa.Table:
        # Each record is uniquely identified by a combination of `namespace` and `id`
        join_fields = ["namespace", "id"]

        # We only consider `compare_cols` changes as a reason to update.
        # Timestamps are used as a gate (must be newer) but should not themselves
        # trigger an update if the content is identical.
        compare_cols = join_fields + self._compare_columns

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

        filtered_ids = updates_projected.column("id")
        update_ids = pa.array(filtered_ids, type=self.schema.field_by_name("id").type)
        return new_data.filter(pc.field("id").isin(update_ids))

    def _filter_by_timestamp(
        self, updates: pa.Table, existing_data: pa.Table
    ) -> pa.Table:
        """Keep updates whose last_modified is newer than the existing record."""
        joined = updates.join(existing_data, keys="id", right_suffix="_existing")

        last_modified_filter = pc.greater(
            joined.column("last_modified"), joined.column("last_modified_existing")
        )
        filtered_ids = joined.filter(last_modified_filter).column("id")
        newer_ids = pa.array(filtered_ids, type=self.schema.field_by_name("id").type)

        return updates.filter(pc.field("id").isin(newer_ids))

    def get_records_by_changeset(self, changeset_id: str) -> pa.Table:
        """Return rows written under the specified changeset ID."""
        return self.table.scan(row_filter=EqualTo("changeset", changeset_id)).to_arrow()
