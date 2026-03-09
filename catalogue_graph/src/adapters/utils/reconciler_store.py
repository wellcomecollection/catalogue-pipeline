import pyarrow as pa
from pyiceberg.table import Table as IcebergTable

from adapters.utils.pipeline_store import PipelineStore
from adapters.utils.schemata import RECONCILER_STORE_ARROW_SCHEMA


class ReconcilerStore(PipelineStore):
    def __init__(self, table: IcebergTable, namespace: str):
        super().__init__(table, namespace)

    @property
    def schema(self) -> pa.Schema:
        return RECONCILER_STORE_ARROW_SCHEMA
