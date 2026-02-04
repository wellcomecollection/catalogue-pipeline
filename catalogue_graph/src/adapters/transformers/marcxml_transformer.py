import io
import logging
from collections.abc import Generator, Iterable
from datetime import datetime
from typing import Any

from pymarc import parse_xml_to_array
from pymarc.record import Record

from adapters.marc.transformers.identifier import extract_id
from adapters.utils.adapter_store import AdapterStore
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
from .base_transformer import BaseTransformer


class MarcXmlTransformer(BaseTransformer):
    def __init__(
        self, adapter_store: AdapterStore, changeset_ids: list[str], identifier_type: Id
    ) -> None:
        super().__init__()
        self.source = AdapterStoreSource(adapter_store, changeset_ids)
        self.identifier_type = identifier_type

    def extract_work_id(self, marc_record: Record) -> str:
        """Extract the work ID from a MARC record.

        Default implementation extracts from MARC 001 field.
        Subclasses can override if adapter-specific ID extraction is needed.
        """
        work_id: str = extract_id(marc_record)
        return work_id

    def transform_record(
        self, work_id: str, marc_record: Record, source_modified_time: datetime
    ) -> InvisibleSourceWork | VisibleSourceWork:
        raise NotImplementedError

    def source_identifier(self, id_value: str) -> SourceIdentifier:
        return SourceIdentifier(
            identifier_type=self.identifier_type, ontology_type="Work", value=id_value
        )

    def source_work_state(
        self,
        id_value: str,
        source_modified_time: datetime,
        relations: WorkRelations | None = None,
    ) -> SourceWorkState:
        current_time_iso: str = convert_datetime_to_utc_iso(datetime.now())
        source_modified_time_iso: str = convert_datetime_to_utc_iso(
            source_modified_time
        )
        return SourceWorkState(
            source_identifier=self.source_identifier(id_value),
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
        self, row_id: str, content: str, source_modified_time: datetime
    ) -> Generator[InvisibleSourceWork | VisibleSourceWork]:
        marc_record: Record | None = None
        try:
            marc_record = self._parse_marc_record(content)
        except Exception as e:
            self._add_error(e, "parse", row_id)
            return

        try:
            work_id = self.extract_work_id(marc_record)
            yield self.transform_record(work_id, marc_record, source_modified_time)
        except Exception as e:
            logging.error(f"Error transforming row_id {row_id}: {e}")
            self._add_error(e, "transform", row_id)

    def _transform_deleted(
        self, content: str, source_modified_time: datetime
    ) -> DeletedSourceWork:
        """Transform a deleted record.

        Parses the MARC content to extract the work ID, ensuring deleted records
        use the same identifier as visible records. Raises an exception if
        parsing fails.
        """
        marc_record = self._parse_marc_record(content)
        work_id = self.extract_work_id(marc_record)

        state = self.source_work_state(work_id, source_modified_time)
        version = int(source_modified_time.timestamp())
        return DeletedSourceWork(
            version=version,
            deleted_reason=DeletedReason(
                type="DeletedFromSource", info="Marked as deleted from source"
            ),
            state=state,
        )

    def transform(
        self, rows: Iterable[dict[str, Any]]
    ) -> Generator[tuple[str, SourceWork]]:
        for row in rows:
            row_id, content, last_modified, is_deleted = (
                row["id"],
                row.get("content"),
                row["last_modified"],
                row.get("deleted", False),
            )

            if not content:
                logging.error(
                    f"Row {row_id} has no content; cannot transform. Skipping."
                )
                self._add_error(Exception("Missing content"), "transform", row_id)
                continue

            if is_deleted:
                yield (row_id, self._transform_deleted(content, last_modified))
            else:
                for work in self._transform_visible(row_id, content, last_modified):
                    yield (row_id, work)
