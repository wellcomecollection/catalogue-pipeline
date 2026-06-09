from collections.abc import Generator, Iterable
from datetime import datetime
from typing import Any

import structlog
from pymarc.record import Record

from adapters.transformers.adapter_store_source import AdapterStoreSource
from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.utils.adapter_store import AdapterStore
from core.transformer import ElasticBaseTransformer
from models.pipeline.source.work import (
    DeletedSourceWork,
    InvisibleSourceWork,
    SourceWork,
    VisibleSourceWork,
)
from utils.marc import parse_single_marc_record

logger = structlog.get_logger(__name__)


class MarcXmlTransformer(ElasticBaseTransformer[SourceWork]):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None = None,
    ) -> None:
        super().__init__()
        self.source: AdapterStoreSource = AdapterStoreSource(
            adapter_store, changeset_ids, snapshot_id
        )

    @property
    def record_transformer(self) -> type[MarcXmlWorkBuilder]:
        raise NotImplementedError

    def _get_document_id(self, record: SourceWork) -> str:
        return record.state.id()

    def _row_to_marc_record(self, row: dict[str, Any]) -> Record | None:
        """
        Convert the row's content field to a MARC record. Return `None` if content field empty or parsing fails.
        """
        row_id, content = row["id"], row.get("content")

        if not content:
            logger.error("Row has no content; cannot transform", row_id=row_id)
            self._add_error(Exception("Missing content"), "transform", row_id)
            return None

        try:
            return parse_single_marc_record(content)
        except Exception as e:
            self._add_error(e, "parse", row["id"])
            return None

    def transform(
        self, rows: Iterable[dict[str, Any]]
    ) -> Generator[tuple[str, SourceWork]]:
        for row in rows:
            marc_record = self._row_to_marc_record(row)
            if not marc_record:
                continue

            row_id, last_modified = row["id"], row["last_modified"]

            try:
                if row.get("deleted", False):
                    yield row_id, self.transform_deleted(marc_record, last_modified)
                else:
                    yield row_id, self.transform_record(marc_record, last_modified)
            except Exception as e:
                logger.error("Error transforming record", row_id=row_id, error=str(e))
                self._add_error(e, "transform", row_id)

    def transform_record(
        self, marc_record: Record, last_modified: datetime
    ) -> InvisibleSourceWork | VisibleSourceWork:
        builder = self.record_transformer(marc_record, last_modified)
        return builder.visible_work

    def transform_deleted(
        self, marc_record: Record, last_modified: datetime
    ) -> DeletedSourceWork:
        builder = self.record_transformer(marc_record, last_modified)
        return builder.deleted_work
