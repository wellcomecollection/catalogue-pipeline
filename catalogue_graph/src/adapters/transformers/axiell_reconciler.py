from collections.abc import Generator, Iterable
from typing import Any

import pyarrow as pa
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

            guid = self.extract_work_id(marc_record)
            data.append(
                {
                    "namespace": self.reconciler_store.namespace,
                    "id": row["id"],
                    "guid": guid,
                    "last_modified": row["last_modified"],
                }
            )

        return pa.Table.from_pylist(data, schema=self.reconciler_store.schema)

    def transform(
        self, rows: Iterable[dict[str, Any]]
    ) -> Generator[tuple[str, DeletedSourceWork]]:
        # Take all rows modified as part of the latest Axiell adapter run and turn them into a PyArrow table
        updated_data = self._rows_to_reconciler_arrow_table(rows)

        # Before updating the reconciler store, save the ID of the current Iceberg snapshot
        # so that we can use it to retrieve overwritten data
        previous_snapshot_id = self.reconciler_store.current_snapshot_id()
        logger.info(
            "Preparing reconciler store update",
            previous_snapshot_id=previous_snapshot_id,
        )

        # Perform an incremental update, updating existing collectId to GUID mappings and adding new mappings.
        result = self.reconciler_store.incremental_update(updated_data)
        if not result:
            logger.info("Reconciler store update did not produce any changes")
            return
        logger.info(
            "Updated reconciler store",
            inserted_count=len(result.inserted_record_ids),
            updated_count=len(result.updated_record_ids),
        )

        # Get *old* mappings which were overwritten as part of the incremental update
        id_filter = In("id", result.updated_record_ids)
        overwritten_data = self.reconciler_store.get_namespace_records(
            id_filter, snapshot_id=previous_snapshot_id
        )

        # Use last_modified from the incoming overwritten rows (not the old snapshot rows)
        last_modified_by_id = {
            row["id"]: row["last_modified"] for row in updated_data.to_pylist()
        }

        # Extract GUIDs (work IDs) from overwritten mappings and emit them as deleted works
        for row in overwritten_data.to_pylist():
            last_modified = last_modified_by_id.get(row["id"], row["last_modified"])
            yield row["id"], self._transform_deleted(row["guid"], last_modified)
