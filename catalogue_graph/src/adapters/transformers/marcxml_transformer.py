from abc import ABC, abstractmethod
from collections.abc import Generator, Iterable
from datetime import datetime
from typing import Any

import structlog
from pymarc.record import Record

from adapters.transformers.marc.identifier import extract_id
from adapters.utils.adapter_store import AdapterStore
from core.transformer import ElasticBaseTransformer
from ingestor.models.shared.deleted_reason import DeletedReason
from models.pipeline.identifier import Id, WorkSourceIdentifier
from models.pipeline.source.work import (
    DeletedSourceWork,
    InvisibleSourceWork,
    SourceWork,
    SourceWorkState,
    VisibleSourceWork,
)
from models.pipeline.work_state import WorkRelations
from utils.marc import parse_single_marc_record
from utils.timezone import convert_datetime_to_utc_iso

from .adapter_store_source import AdapterStoreSource

logger = structlog.get_logger(__name__)


class MarcXmlTransformer(ElasticBaseTransformer[SourceWork], ABC):
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

    def _get_document_id(self, record: SourceWork) -> str:
        return record.state.id()

    @property
    @abstractmethod
    def source_identifier_type(self) -> Id: ...

    @property
    def predecessor_identifier_type(self) -> Id:
        raise NotImplementedError

    def extract_work_id(self, marc_record: Record) -> str:
        """Extract the work ID from a MARC record.

        Default implementation extracts from MARC 001 field.
        Subclasses can override if adapter-specific ID extraction is needed.
        """
        work_id: str = extract_id(marc_record)
        return work_id

    def extract_predecessor_id(self, marc_record: Record) -> str | None:
        """Extract the predecessor identifier from a MARC record.

        Returns None by default. Subclasses should override to extract
        from the appropriate field with adapter-specific validation.
        """
        return None

    def transform_record(
        self, marc_record: Record, source_modified_time: datetime
    ) -> InvisibleSourceWork | VisibleSourceWork:
        raise NotImplementedError

    def transform_deleted(
        self, marc_record: Record, source_modified_time: datetime
    ) -> DeletedSourceWork:
        work_id = self.extract_work_id(marc_record)
        return self.deleted_from_work_id(work_id, source_modified_time)

    def deleted_from_work_id(
        self, work_id: str, source_modified_time: datetime
    ) -> DeletedSourceWork:
        state = self.construct_work_state(work_id, source_modified_time)
        version = int(source_modified_time.timestamp())
        return DeletedSourceWork(
            version=version,
            deleted_reason=DeletedReason(
                type="DeletedFromSource", info="Marked as deleted from source"
            ),
            state=state,
        )

    def source_work_state(
        self,
        marc_record: Record,
        source_modified_time: datetime,
        relations: WorkRelations | None = None,
    ) -> SourceWorkState:
        return self.construct_work_state(
            work_id=self.extract_work_id(marc_record),
            predecessor_id=self.extract_predecessor_id(marc_record),
            source_modified_time=source_modified_time,
            relations=relations,
        )

    def construct_work_state(
        self,
        work_id: str,
        source_modified_time: datetime,
        predecessor_id: str | None = None,
        relations: WorkRelations | None = None,
    ) -> SourceWorkState:
        predecessor_identifier = None
        if predecessor_id is not None:
            predecessor_identifier = WorkSourceIdentifier(
                identifier_type=self.predecessor_identifier_type,
                value=predecessor_id,
            )

        return SourceWorkState(
            source_identifier=WorkSourceIdentifier(
                identifier_type=self.source_identifier_type,
                value=work_id,
            ),
            predecessor_identifier=predecessor_identifier,
            source_modified_time=convert_datetime_to_utc_iso(source_modified_time),
            modified_time=convert_datetime_to_utc_iso(datetime.now()),
            relations=relations,
        )

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
