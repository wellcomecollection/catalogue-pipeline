from abc import ABC, abstractmethod
from typing import Any

import structlog
from pymarc.record import Record

from adapters.transformers.adapter_store_source import AdapterStoreSource
from adapters.transformers.builders.source_work_builder import SourceWorkBuilder
from adapters.utils.adapter_store import AdapterStore
from core.transformer import ElasticBaseTransformer
from models.pipeline.source.work import (
    SourceWork,
)
from utils.marc import parse_single_marc_record

logger = structlog.get_logger(__name__)


class SourceWorkTransformer(ElasticBaseTransformer[SourceWork], ABC):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None = None,
        items_store: AdapterStore | None = None,
    ) -> None:
        super().__init__()
        self.source: AdapterStoreSource = AdapterStoreSource(
            adapter_store, changeset_ids, snapshot_id, items_store=items_store
        )

    @property
    @abstractmethod
    def work_builder(self) -> type[SourceWorkBuilder]:
        """
        The subclass used to convert individual records into works. Must be overridden
        by each concrete transformer.
        """

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
            logger.error("Failed to parse MARC record", row_id=row_id, error=str(e))
            self._add_error(e, "parse", row["id"])
            return None
