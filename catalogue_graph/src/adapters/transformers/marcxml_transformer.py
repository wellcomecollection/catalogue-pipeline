from abc import ABC, abstractmethod
from collections.abc import Generator, Iterable
from datetime import datetime
from typing import Any

import structlog
from elasticsearch import Elasticsearch
from pymarc.record import Record

from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.marc.identifier import has_id
from adapters.transformers.source_work_transformer import SourceWorkTransformer
from ingestor.models.shared.deleted_reason import DeletedFromSource
from models.pipeline.source.work import (
    DeletedSourceWork,
    SourceWork,
)

logger = structlog.get_logger(__name__)


class MarcXmlTransformer(SourceWorkTransformer, ABC):
    def __init__(self, *args: Any, **kwargs: Any) -> None:
        super().__init__(*args, **kwargs)
        self.skipped_no_id_count = 0

    @property
    @abstractmethod
    def work_builder(self) -> type[MarcXmlWorkBuilder]: ...

    def stream_to_index(self, es_client: Elasticsearch, index_name: str) -> None:
        self.skipped_no_id_count = 0
        super().stream_to_index(es_client, index_name)
        # One summary line per run instead of a warning per record (platform#6619).
        if self.skipped_no_id_count:
            logger.warning(
                "Skipped records with a missing or empty id field (001)",
                skipped_count=self.skipped_no_id_count,
            )

    def transform(
        self, rows: Iterable[dict[str, Any]]
    ) -> Generator[tuple[str, SourceWork]]:
        for row in rows:
            yield from self._transform_row(row)

    def _transform_row(self, row: dict[str, Any]) -> Generator[tuple[str, SourceWork]]:
        """Transform a single row, yielding at most one (row_id, work) tuple.
        Subclasses override this to handle non-MARC rows (e.g. deletion facts)."""
        marc_record = self._row_to_marc_record(row)
        if not marc_record:
            return

        # A record with no id cannot be processed for any source, so skip it
        # (no work, no deletion, no failure) rather than error in the builder.
        if not has_id(marc_record):
            self.skipped_no_id_count += 1
            return

        row_id, last_modified = row["id"], row["last_modified"]
        # Item/holdings enrichment content joined on by FolioStoreSource
        # (None for adapters without an items store).
        enrichment_content = row.get("enrichment_content")

        try:
            if row.get("deleted", False):
                yield row_id, self.transform_deleted(marc_record, last_modified)
            else:
                yield (
                    row_id,
                    self.transform_record(
                        marc_record, last_modified, enrichment_content
                    ),
                )
        except Exception as e:
            logger.error("Error transforming record", row_id=row_id, error=str(e))
            self._add_error(e, "transform", row_id)

    def transform_record(
        self,
        marc_record: Record,
        last_modified: datetime,
        enrichment_content: str | None = None,
    ) -> SourceWork:
        builder = self.work_builder(
            marc_record, last_modified, enrichment_content=enrichment_content
        )
        return builder.transform_work()

    def transform_deleted(
        self, marc_record: Record, last_modified: datetime
    ) -> DeletedSourceWork:
        builder = self.work_builder(marc_record, last_modified)
        return builder.transform_deleted_work(deleted_reason=DeletedFromSource())
