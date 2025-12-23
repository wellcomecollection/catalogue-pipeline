import io
import logging
from collections.abc import Generator, Iterable
from datetime import datetime
from typing import Any

from pymarc import parse_xml_to_array
from pymarc.record import Record

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

    def transform_record(
        self, marc_record: Record, source_modified_time: datetime
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

    def _transform_visible(
        self, work_id: str, content: str, source_modified_time: datetime
    ) -> Generator[InvisibleSourceWork | VisibleSourceWork]:
        marc_records: list[Record] = []
        try:
            marc_records = parse_xml_to_array(io.StringIO(content))
            assert len(marc_records) == 1
        except Exception as e:
            self._add_error(e, "parse", work_id)

        try:
            for record in marc_records:
                yield self.transform_record(record, source_modified_time)
        except Exception as e:
            logging.error(f"Error transforming work_id {work_id}: {e}")
            self._add_error(e, "transform", work_id)

    def _transform_deleted(
        self, work_id: str, source_modified_time: datetime
    ) -> DeletedSourceWork:
        # Deleted works require a version and type; use timestamp similar to visible works
        state = self.source_work_state(work_id, source_modified_time)
        version = int(source_modified_time.timestamp())
        return DeletedSourceWork(
            version=version,
            deleted_reason=DeletedReason(
                type="DeletedFromSource", info="Marked as deleted from source"
            ),
            state=state,
        )

    def transform(self, rows: Iterable[dict[str, Any]]) -> Generator[SourceWork]:
        for row in rows:
            work_id, content, last_modified = (
                row["id"],
                row.get("content"),
                row["last_modified"],
            )

            if not content:
                yield self._transform_deleted(work_id, last_modified)
            else:
                yield from self._transform_visible(work_id, content, last_modified)
