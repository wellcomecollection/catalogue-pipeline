import io
from collections.abc import Generator, Iterable
from datetime import datetime
from typing import Any

import structlog
from pymarc import parse_xml_to_array
from pymarc.record import Record

from adapters.transformers.marc.identifier import extract_id
from adapters.transformers.marc.predecessor_identifier import extract_predecessor_id
from adapters.utils.adapter_store import AdapterStore
from core.transformer import ElasticBaseTransformer
from ingestor.models.shared.deleted_reason import DeletedReason
from models.pipeline.identifier import Id, SourceIdentifier
from models.pipeline.source.work import (
    DeletedSourceWork,
    InvisibleSourceWork,
    SourceWork,
    SourceWorkState,
    VisibleSourceWork,
)
from models.pipeline.work_state import WorkRelations
from utils.timezone import convert_datetime_to_utc_iso

from .adapter_store_source import AdapterStoreSource

logger = structlog.get_logger(__name__)


class MarcXmlTransformer(ElasticBaseTransformer[SourceWork]):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        identifier_type: Id,
        predecessor_identifier_type: Id | None = None,
        snapshot_id: int | None = None,
    ) -> None:
        super().__init__()
        self.source: AdapterStoreSource = AdapterStoreSource(
            adapter_store, changeset_ids, snapshot_id
        )
        self.identifier_type = identifier_type
        self.predecessor_identifier_type = predecessor_identifier_type

    def _get_document_id(self, record: SourceWork) -> str:
        return record.state.id()

    def extract_work_id(self, marc_record: Record) -> str:
        """Extract the work ID from a MARC record.

        Default implementation extracts from MARC 001 field.
        Subclasses can override if adapter-specific ID extraction is needed.
        """
        work_id: str = extract_id(marc_record)
        return work_id

    def transform_record(
        self, marc_record: Record, source_modified_time: datetime
    ) -> InvisibleSourceWork | VisibleSourceWork:
        raise NotImplementedError

    def source_identifier(self, id_value: str, identifier_type: Id) -> SourceIdentifier:
        return SourceIdentifier(
            identifier_type=identifier_type, ontology_type="Work", value=id_value
        )

    def source_work_state(
        self,
        marc_record: Record,
        source_modified_time: datetime,
        relations: WorkRelations | None = None,
    ) -> SourceWorkState:
        current_time_iso: str = convert_datetime_to_utc_iso(datetime.now())
        source_modified_time_iso: str = convert_datetime_to_utc_iso(
            source_modified_time
        )
        source_id = self.extract_work_id(marc_record)
        predecessor_id = extract_predecessor_id(marc_record)

        return SourceWorkState(
            source_identifier=self.source_identifier(source_id, self.identifier_type),
            predecessor_identifier=self.source_identifier(
                predecessor_id, self.predecessor_identifier_type
            )
            if predecessor_id and self.predecessor_identifier_type
            else None,
            source_modified_time=source_modified_time_iso,
            modified_time=current_time_iso,
            relations=relations,
        )

    def _parse_marc_record(self, content: str) -> Record:
        """Parse MARC XML content and return a single Record.

        Raises an exception if parsing fails or if the content does not
        contain exactly one record.
        """
        marc_records = parse_xml_to_array(io.StringIO(content))
        assert len(marc_records) == 1, f"Expected 1 record, got {len(marc_records)}"
        return marc_records[0]

    def _transform_visible(
        self, row_id: str, marc_record: Record, source_modified_time: datetime
    ) -> Generator[InvisibleSourceWork | VisibleSourceWork]:
        try:
            yield self.transform_record(marc_record, source_modified_time)
        except Exception as e:
            logger.error("Error transforming record", row_id=row_id, error=str(e))
            self._add_error(e, "transform", row_id)

    def _transform_deleted(
        self, work_id: str, source_modified_time: datetime
    ) -> DeletedSourceWork:
        """Transform a deleted record."""
        state = SourceWorkState(
            source_identifier=self.source_identifier(work_id, self.identifier_type),
            source_modified_time=convert_datetime_to_utc_iso(source_modified_time),
            modified_time=convert_datetime_to_utc_iso(datetime.now()),
        )
        version = int(source_modified_time.timestamp())
        return DeletedSourceWork(
            version=version,
            deleted_reason=DeletedReason(
                type="DeletedFromSource", info="Marked as deleted from source"
            ),
            state=state,
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
            return self._parse_marc_record(content)
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
            if row.get("deleted", False):
                work_id = self.extract_work_id(marc_record)
                yield row_id, self._transform_deleted(work_id, last_modified)
            else:
                for work in self._transform_visible(row_id, marc_record, last_modified):
                    yield row_id, work
