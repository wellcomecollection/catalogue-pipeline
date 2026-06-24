from abc import ABC, abstractmethod
from collections.abc import Generator, Iterable
from datetime import datetime
from typing import Any

import structlog
from pymarc.record import Record

from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.source_work_transformer import SourceWorkTransformer
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
    ) -> SourceWork:
        builder = self.work_builder(marc_record, last_modified)
        return builder.transform_work()

    def transform_deleted(
        self, marc_record: Record, last_modified: datetime
    ) -> DeletedSourceWork:
        builder = self.work_builder(marc_record, last_modified)
        return builder.transform_deleted_work(deleted_reason=DeletedFromSource())
