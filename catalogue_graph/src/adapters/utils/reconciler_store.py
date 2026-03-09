from typing import cast

import pyarrow as pa
from pyiceberg.expressions import In
from pyiceberg.table import Table as IcebergTable

from adapters.utils.pipeline_store import PipelineStore, PipelineStoreUpdate
from adapters.utils.schemata import RECONCILER_STORE_ARROW_SCHEMA


class ReconcilerStore(PipelineStore):
    """Store for reconciler guid mappings with incremental updates."""

    def __init__(self, table: IcebergTable, namespace: str):
        super().__init__(table, namespace)

    @property
    def schema(self) -> pa.Schema:
        return RECONCILER_STORE_ARROW_SCHEMA

    def incremental_update(self, new_data: pa.Table) -> PipelineStoreUpdate | None:
        """Insert new records and update changed guid mappings."""
        new_data = self._cast_to_arrow_schema(new_data, operation="incremental_update")

        if new_data.num_rows == 0:
            return None

        # Fetch rows that match the incoming IDs
        incoming_ids = cast(list[str], new_data.column("id").to_pylist())
        existing_data = self.get_records_in_namespace(In("id", incoming_ids))

        if existing_data.num_rows > 0:
            updates = self._find_updates(existing_data, new_data, compare_cols=["guid"])
            # Filter updates to only include records with newer timestamps
            changes = self._filter_by_timestamp(updates, existing_data)
            inserts = self._find_inserts(existing_data, new_data)
        else:
            inserts = new_data
            changes = None

        return self._upsert_with_markers(changes, inserts)
