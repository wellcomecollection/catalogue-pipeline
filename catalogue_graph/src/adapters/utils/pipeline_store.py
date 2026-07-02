import uuid
from abc import ABC, abstractmethod
from datetime import datetime
from typing import Any, NamedTuple, cast

import pyarrow as pa
import pyarrow.compute as pc
from pydantic import BaseModel
from pyiceberg.expressions import (
    And,
    BooleanExpression,
    EqualTo,
    GreaterThanOrEqual,
    In,
)
from pyiceberg.table import ALWAYS_TRUE
from pyiceberg.table import Table as IcebergTable
from pyiceberg.table.upsert_util import get_rows_to_update


class ChangesetRecordIds(NamedTuple):
    """The record IDs written under a set of changesets, with the minimum
    `last_modified` across them (a file-pruning bound for id-based reads)."""

    ids: list[str]
    min_last_modified: datetime | None


class PipelineStoreUpdate(BaseModel):
    changeset_id: str
    inserted_record_ids: list[str]  # New records
    updated_record_ids: list[str]  # Existing updated records

    @property
    def upserted_record_ids(self) -> list[str]:
        """Return both inserted and updated record IDs"""
        return self.updated_record_ids + self.inserted_record_ids


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

    def current_snapshot_id(self) -> int | None:
        snapshot = self.table.current_snapshot()
        return snapshot.snapshot_id if snapshot else None

    def get_namespace_records(
        self,
        iceberg_filter: BooleanExpression = ALWAYS_TRUE,
        snapshot_id: int | None = None,
    ) -> pa.Table:
        """Return records in the store namespace, optionally filtered."""
        full_filter = And(EqualTo("namespace", self.namespace), iceberg_filter)
        return (
            self.table.scan(row_filter=full_filter, snapshot_id=snapshot_id)
            .to_arrow()
            .cast(self.schema)
        )

    def stream_namespace_records(
        self,
        iceberg_filter: BooleanExpression = ALWAYS_TRUE,
        snapshot_id: int | None = None,
    ) -> pa.RecordBatchReader:
        """Stream records in the store namespace as Arrow record batches.

        Unlike `get_namespace_records`, rows are not materialised as a single
        table. Note that pyiceberg's reader prefetches data files on a thread
        pool regardless of consumption rate (apache/iceberg-python#2407), so
        peak memory is well below an eager read but can still approach the
        table size when the consumer is slower than the downloads.
        """
        full_filter = And(EqualTo("namespace", self.namespace), iceberg_filter)
        return (
            self.table.scan(row_filter=full_filter, snapshot_id=snapshot_id)
            .to_arrow_batch_reader()
            .cast(self.schema)
        )

    def get_records_by_changeset(
        self, changeset_id: str, snapshot_id: int | None = None
    ) -> pa.Table:
        """Return rows written under the specified changeset ID."""
        return self.get_namespace_records(
            EqualTo("changeset", changeset_id), snapshot_id
        )

    def get_records_by_ids(
        self,
        ids: list[str],
        snapshot_id: int | None = None,
        updated_since: datetime | None = None,
    ) -> pa.Table:
        """Return rows with the specified record IDs, including soft-deleted rows.

        Unlike a changeset filter, an `id` filter prunes data files on the
        id-sorted table, so this read is cheap when `ids` spans few files.

        `updated_since` is a pruning bound, not a selection filter: callers must
        ensure every target row's `last_modified` is at or after it (e.g. the
        minimum across a changeset's rows). It lets file-level column stats
        skip old compacted files whose id ranges overlap the target ids but
        which cannot contain the recently written rows.
        """
        row_filter: BooleanExpression = In("id", ids)
        if updated_since is not None:
            row_filter = And(
                row_filter,
                GreaterThanOrEqual("last_modified", updated_since.isoformat()),
            )
        return self.get_namespace_records(row_filter, snapshot_id)

    def get_changeset_record_ids(
        self, changeset_ids: list[str], snapshot_id: int | None = None
    ) -> ChangesetRecordIds:
        """Return the IDs of rows written under any of the specified changesets,
        with the minimum `last_modified` across them (for use as a pruning
        bound in `get_records_by_ids`).

        Projects only the `id` and `last_modified` columns, so the scan avoids
        reading row content and stays cheap even though the changeset filter
        cannot prune data files on the id-sorted table.
        """
        full_filter = And(
            EqualTo("namespace", self.namespace), In("changeset", changeset_ids)
        )
        id_table = self.table.scan(
            row_filter=full_filter,
            snapshot_id=snapshot_id,
            selected_fields=("id", "last_modified"),
        ).to_arrow()

        timestamps = cast(
            list[datetime | None], id_table.column("last_modified").to_pylist()
        )
        # A null last_modified would make the pruning bound exclude that row,
        # so only supply a bound when every row carries a timestamp.
        non_null = [t for t in timestamps if t is not None]
        min_last_modified = (
            min(non_null) if len(non_null) == len(timestamps) and non_null else None
        )
        return ChangesetRecordIds(self._extract_ids(id_table), min_last_modified)

    def normalise_table(self, table: pa.Table) -> pa.Table:
        """Enforce that the table conforms to the required schema and filter for records in the selected namespace"""
        table = table.cast(self.schema)
        return table.filter(pc.field("namespace") == self.namespace)

    def incremental_update(self, new_data: pa.Table) -> PipelineStoreUpdate | None:
        """Apply an incremental update for incoming rows.

        Any rows whose IDs do not exist in the current table are inserted. Any updated rows (chosen by comparing
        the subclass's compare columns and filtering for rows with a newer `last_modified` timestamp) are updated.
        Subclasses may apply a final transformation to the update set (e.g. preserving content for deletions)
        before writes.
        """
        new_data = self.normalise_table(new_data)
        if new_data.num_rows == 0:
            return None

        new_ids_filter = In("id", self._extract_ids(new_data))
        existing_data = self.get_namespace_records(new_ids_filter)

        if existing_data.num_rows > 0:
            updates = self._find_updates(existing_data, new_data)
            updates = self._filter_updates_by_timestamp(updates, existing_data)
            updates = self._transform_incremental_updates(updates, existing_data)
            inserts = self._find_inserts(existing_data, new_data)
            changes = updates
        else:
            inserts = new_data
            changes = None

        return self._commit_changeset(changes, inserts)

    def _transform_incremental_updates(
        self, updates: pa.Table, existing_data: pa.Table
    ) -> pa.Table:
        """Apply store-specific update transformations before writing."""
        return updates

    def _commit_changeset(
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
                overwrite_filter = And(
                    EqualTo("namespace", self.namespace),
                    In("id", self._extract_ids(changes)),
                )
                tx.overwrite(changes, overwrite_filter=overwrite_filter)
            if inserts is not None:
                inserts = self._set_changeset_id(inserts, changeset_id)
                tx.append(inserts)

        inserted_ids = self._extract_ids(inserts) if inserts else []
        changed_ids = self._extract_ids(changes) if changes else []
        return PipelineStoreUpdate(
            changeset_id=changeset_id,
            inserted_record_ids=inserted_ids,
            updated_record_ids=changed_ids,
        )

    def _extract_ids(self, table: pa.Table) -> list[str]:
        # to_pylist returns `list[Any | None]`, so we need to explicitly cast to `list[str]`.
        # This is safe, since typing is enforced at the schema level.
        return cast(list[str], table.column("id").to_pylist())

    def _set_column_value(
        self, changeset: pa.Table, field_name: str, value: Any
    ) -> pa.Table:
        field_index = changeset.schema.get_field_index(field_name)
        field = self.schema.field(field_name)
        values = pa.repeat(value, changeset.num_rows).cast(field.type)
        return changeset.set_column(field_index, field, values)

    def _set_last_modified_timestamp(
        self, changeset: pa.Table, timestamp: datetime
    ) -> pa.Table:
        return self._set_column_value(changeset, "last_modified", timestamp)

    def _set_changeset_id(self, changeset: pa.Table, changeset_id: str) -> pa.Table:
        return self._set_column_value(changeset, "changeset", changeset_id)

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
        update_ids = pa.array(filtered_ids, type=self.schema.field("id").type)
        return new_data.filter(pc.field("id").isin(update_ids))

    def _filter_updates_by_timestamp(
        self, updates: pa.Table, existing_data: pa.Table
    ) -> pa.Table:
        """Keep updates whose last_modified is newer than the existing record."""
        joined = updates.join(existing_data, keys="id", right_suffix="_existing")

        last_modified_filter = pc.greater(
            joined.column("last_modified"), joined.column("last_modified_existing")
        )
        filtered_ids = joined.filter(last_modified_filter).column("id")
        newer_ids = pa.array(filtered_ids, type=self.schema.field("id").type)

        return updates.filter(pc.field("id").isin(newer_ids))
