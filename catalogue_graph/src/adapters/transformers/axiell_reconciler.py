import logging
from collections.abc import Generator, Iterable
from datetime import UTC, datetime
from typing import Any

import pyarrow as pa
from pyiceberg.expressions import In

from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.reconciler_store import ReconcilerStore
from models.pipeline.identifier import Id
from models.pipeline.source.work import (
    DeletedSourceWork,
)


class AxiellReconciler(MarcXmlTransformer):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        reconciler_store: ReconcilerStore,
    ) -> None:
        self.reconciler_store = reconciler_store
        super().__init__(adapter_store, changeset_ids, Id(id="axiell-guid"))

    def transform(
        self, rows: Iterable[dict[str, Any]]
    ) -> Generator[tuple[str, DeletedSourceWork]]:
        data = []

        for row in rows:
            row_id, content, last_modified = (
                row["id"],
                row.get("content"),
                row["last_modified"],
            )

            if not content:
                logging.error(f"Row {row_id} has no content; cannot extract GUID.")
                self._add_error(Exception("Missing content"), "transform", row_id)
                continue

            marc_record = self._parse_marc_record(content)
            guid = self.extract_work_id(marc_record)
            data.append(
                {
                    "namespace": "axiell",
                    "id": row_id,
                    "guid": guid,
                    "changeset": None,
                    "last_modified": last_modified,
                }
            )

        before_transaction = datetime.now(UTC)

        updated_data = pa.Table.from_pylist(data, schema=self.reconciler_store.schema)
        result = self.reconciler_store.incremental_update(updated_data)

        if not result:
            return

        # Get overwritten collectId to GUID mappings using a snapshot
        id_filter = In("id", result.changed_record_ids)
        overwritten_data = self.reconciler_store.get_namespace_records(
            id_filter, as_of_time=before_transaction
        )

        for row in overwritten_data.to_pylist():
            content, last_modified = row.get("content"), row["last_modified"]
            if not content:
                continue
            yield row["id"], self._transform_deleted(content, last_modified)
