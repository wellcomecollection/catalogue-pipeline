from collections.abc import Generator, Iterable
from typing import Any

import pyarrow as pa
import pyarrow.compute as pc
import structlog
from pyiceberg.expressions import In

from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.reconciler_store import ReconcilerStore
from models.pipeline.identifier import Id
from models.pipeline.source.work import (
    DeletedSourceWork,
)

logger = structlog.get_logger(__name__)


class AxiellReconciler(MarcXmlTransformer):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        reconciler_store: ReconcilerStore,
    ) -> None:
        self.reconciler_store = reconciler_store
        super().__init__(adapter_store, changeset_ids, Id(id="axiell-guid"))

    def _rows_to_reconciler_arrow_table(
        self, rows: Iterable[dict[str, Any]]
    ) -> pa.Table:
        data = []
        for row in rows:
            marc_record = self._row_to_marc_record(row)
            if not marc_record:
                continue

            data.append(
                {
                    "namespace": self.reconciler_store.namespace,
                    "id": row["id"],
                    "guid": self.extract_work_id(marc_record),
                    "last_modified": row["last_modified"],
                }
            )

        return pa.Table.from_pylist(data, schema=self.reconciler_store.schema)

    def transform(
        self, rows: Iterable[dict[str, Any]]
    ) -> Generator[tuple[str, DeletedSourceWork]]:
        updated_adapter_mappings = self._rows_to_reconciler_arrow_table(rows)

        # Get a list of row IDs whose GUID mappings are about to be updated in the reconciler store
        record_ids_to_overwrite = self.reconciler_store.get_ids_to_update(
            updated_adapter_mappings
        )

        # Get the corresponding GUID mappings
        id_filter = In("id", record_ids_to_overwrite)
        data_to_overwrite = self.reconciler_store.get_namespace_records(id_filter)

        # Use last_modified from the incoming rows (not the rows which are about to be overwritten)
        last_modified_by_id = {
            row["id"]: row["last_modified"]
            for row in updated_adapter_mappings.to_pylist()
        }

        # Emit each row as a deleted work
        for row in data_to_overwrite.to_pylist():
            last_modified = last_modified_by_id.get(row["id"], row["last_modified"])
            yield row["id"], self._transform_deleted(row["guid"], last_modified)

    def _commit(
        self, rows: Iterable[dict[str, Any]], _: set[str], error_row_ids: set[str]
    ) -> None:
        updated_data = self._rows_to_reconciler_arrow_table(rows)

        # If a work corresponding to some row ID wasn't successfully marked as deleted in Elasticsearch,
        # do not commit the new GUID mapping
        id_type = self.reconciler_store.schema.field("id").type
        error_ids = pa.array(error_row_ids, type=id_type)
        updated_data = updated_data.filter(~pc.field("id").isin(error_ids))

        commit_result = self.reconciler_store.incremental_update(updated_data)
        if not commit_result:
            return

        logger.info(
            "Committed reconciler mappings",
            inserted_count=len(commit_result.inserted_record_ids),
            updated_count=len(commit_result.updated_record_ids),
        )
