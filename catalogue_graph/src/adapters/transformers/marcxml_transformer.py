from abc import ABC, abstractmethod
from collections.abc import Generator, Iterable
from typing import Any

import structlog

from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.source_work_transformer import SourceWorkTransformer
from models.pipeline.source.work import (
    DeletedSourceWork,
    SourceWork,
    VisibleSourceWork,
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
                builder = self.work_builder(marc_record, last_modified)
                if builder.is_deleted(row):
                    yield row_id, self.transform_deleted(builder)
                else:
                    yield row_id, self.transform_record(builder)
            except Exception as e:
                logger.error("Error transforming record", row_id=row_id, error=str(e))
                self._add_error(e, "transform", row_id)

    def transform_record(self, builder: MarcXmlWorkBuilder) -> VisibleSourceWork:
        return builder.visible_work

    def transform_deleted(self, builder: MarcXmlWorkBuilder) -> DeletedSourceWork:
        return builder.deleted_work
