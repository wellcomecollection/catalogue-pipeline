import pyarrow as pa
from pyiceberg.expressions import In
from pyiceberg.table import Table as IcebergTable

from adapters.utils.pipeline_store import PipelineStore
from adapters.utils.schemata import RECONCILER_STORE_ARROW_SCHEMA


class ReconcilerStore(PipelineStore):
    """Store for reconciler guid mappings with incremental updates."""

    def __init__(self, table: IcebergTable, namespace: str):
        super().__init__(table, namespace)

    @property
    def schema(self) -> pa.Schema:
        return RECONCILER_STORE_ARROW_SCHEMA

    @property
    def _compare_columns(self) -> list[str]:
        return ["guid"]

    def get_ids_to_update(self, new_data: pa.Table) -> list[str]:
        new_data = self.normalise_table(new_data)
        new_ids_filter = In("id", self._extract_ids(new_data))
        existing_data = self.get_namespace_records(new_ids_filter)

        updates = self._find_updates(existing_data, new_data)
        updates = self._filter_updates_by_timestamp(updates, existing_data)
        return self._extract_ids(updates) if updates else []
