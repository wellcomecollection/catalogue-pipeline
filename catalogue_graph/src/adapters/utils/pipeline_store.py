import uuid
from abc import ABC, abstractmethod
from datetime import datetime
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
                f"{operation} requires new_data to be castable to schema: {self.schema}; "
                f"got schema: {new_data.schema}"
            ) from e

    def _get_existing_data(self, row_filter: BooleanExpression) -> pa.Table:
        return self.table.scan(row_filter=row_filter).to_arrow().cast(self.schema)

    def _upsert_with_markers(
        self, changes: pa.Table | None, inserts: pa.Table | None
    ) -> AdapterStoreUpdate:
        """
        Insert and update records, adding changeset values to any changed rows.
        :param changes: New versions of existing records to change
        :param inserts: New records to insert
        """
        changeset_id = str(uuid.uuid1())
        if changes is not None:
            changes = self._set_changeset_id(changes, changeset_id)
        if inserts is not None:
            inserts = self._set_changeset_id(inserts, changeset_id)
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
        ids = changes.column("id").drop_null().to_pylist()
        return In("id", ids)

    @staticmethod
    def _set_last_modified(changeset: pa.Table, timestamp: datetime) -> pa.Table:
        idx = changeset.schema.get_field_index("last_modified")
        changeset = changeset.set_column(
            idx, LAST_MODIFIED_FIELD, pa.repeat(timestamp, changeset.num_rows)
        )
        return changeset

    @staticmethod
    def _set_changeset_id(changeset: pa.Table, changeset_id: str) -> pa.Table:
        idx = changeset.schema.get_field_index("changeset")
        changeset = changeset.set_column(
            idx, CHANGESET_FIELD, pa.repeat(changeset_id, changeset.num_rows)
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

        if len(updates_projected) == 0:
            return new_data.slice(0, 0)

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
        joined = updates.join(existing_data, keys="id", right_suffix="_existing")
        existing_ts = joined.column("last_modified_existing")
        last_modified_filter = pc.or_(
            pc.is_null(existing_ts),
            pc.fill_null(
                pc.greater(joined.column("last_modified"), existing_ts), False
            ),
        )
        newer_ids = joined.filter(last_modified_filter).column("id")
        return updates.filter(
            pc.field("id").isin(pa.array(newer_ids, type=pa.string()))
        )

    def get_records_by_changeset(self, changeset_id: str) -> pa.Table:
        return self.table.scan(row_filter=EqualTo("changeset", changeset_id)).to_arrow()
