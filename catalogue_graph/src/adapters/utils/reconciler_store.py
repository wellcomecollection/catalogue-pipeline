from pydantic import BaseModel
from pyiceberg.schema import Schema
from pyiceberg.table import Table as IcebergTable

from adapters.utils.pipeline_store import PipelineStore
from adapters.utils.schemata import RECONCILER_STORE_ARROW_SCHEMA


class AdapterStoreUpdate(BaseModel):
    changeset_id: str
    updated_record_ids: list[str]


class ReconcilerStore(PipelineStore):
    def __init__(self, table: IcebergTable, namespace: str):
        super().__init__(table, namespace)

    @property
    def schema(self) -> Schema:
        return RECONCILER_STORE_ARROW_SCHEMA
