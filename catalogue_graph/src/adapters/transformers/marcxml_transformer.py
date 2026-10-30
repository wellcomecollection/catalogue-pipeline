from abc import ABC, abstractmethod
from collections.abc import Generator, Iterable
from typing import Any

import structlog
from pymarc.record import Record

from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.marc.identifier import has_id
from adapters.transformers.source_work_transformer import SourceWorkTransformer
from core.document import Document
from ingestor.models.shared.deleted_reason import DeletedFromSource
from models.pipeline.source.work import (
    DeletedSourceWork,
    SourceWork,
)

logger = structlog.get_logger(__name__)


class MarcXmlTransformer(SourceWorkTransformer, ABC):
    @property
    @abstractmethod
    def work_builder(self) -> type[MarcXmlWorkBuilder]: ...

    def transform(self, rows: Iterable[dict[str, Any]]) -> Generator[Document]:
        for row in rows:
            # Bind the row id so every log line emitted while transforming the
            # record references it, including those from the field extractors.
            with structlog.contextvars.bound_contextvars(row_id=row["id"]):
                documents = list(self._transform_row(row))
            yield from documents

    def _transform_row(self, row: dict[str, Any]) -> Generator[Document]:
        """Transform a single row, yielding at most one document.
        Subclasses override this to handle non-MARC rows (e.g. deletion facts)."""
        # A tombstone first seen as deleted never had content, so never produced a work.
        if row.get("deleted", False) and not row.get("content"):
            logger.warning("Skipping deleted row with no content")
            return

        marc_record = self._row_to_marc_record(row)
        if not marc_record:
            return

        # A record with no id cannot be processed for any source, so skip it
        # (no work, no deletion, no failure) rather than error in the builder.
        if not has_id(marc_record):
            logger.warning("Skipping record with a missing or empty id field (001)")
            return

        row_id = row["id"]
        try:
            work: SourceWork
            if row.get("deleted", False):
                work = self.transform_deleted(marc_record, row)
            else:
                work = self.transform_record(marc_record, row)
            yield self.document(row_id, work)
        except Exception as e:
            logger.error("Error transforming record", error=str(e))
            self._add_error(e, "transform", row_id)

    def _make_builder(
        self, marc_record: Record, row: dict[str, Any]
    ) -> MarcXmlWorkBuilder:
        """Build the work builder for a row. Override to pass source-specific inputs."""
        return self.work_builder(marc_record, row["last_modified"])

    def transform_record(self, marc_record: Record, row: dict[str, Any]) -> SourceWork:
        return self._make_builder(marc_record, row).transform_work()

    def transform_deleted(
        self, marc_record: Record, row: dict[str, Any]
    ) -> DeletedSourceWork:
        return self._make_builder(marc_record, row).transform_deleted_work(
            deleted_reason=DeletedFromSource()
        )
