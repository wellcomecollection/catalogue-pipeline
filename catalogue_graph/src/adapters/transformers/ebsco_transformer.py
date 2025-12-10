import io
from collections.abc import Generator, Iterable
from typing import Any

import dateutil.parser
from pymarc import parse_xml_to_array
from pymarc.record import Record

from adapters.ebsco.transformers.ebsco_to_weco import (
    ebsco_source_work_state,
    transform_record,
)
from adapters.utils.adapter_store import AdapterStore
from ingestor.models.shared.deleted_reason import DeletedReason
from models.pipeline.source.work import DeletedSourceWork, SourceWork, VisibleSourceWork

from .adapter_store_source import AdapterStoreSource
from .base_transformer import BaseTransformer


class EbscoTransformer(BaseTransformer):
    def __init__(self, adapter_store: AdapterStore, changeset_ids: list[str]) -> None:
        super().__init__()
        self.source = AdapterStoreSource(adapter_store, changeset_ids)

    @staticmethod
    def _transform_deleted(work_id: str) -> DeletedSourceWork:
        # Deleted works require a version and type; use timestamp similar to visible works
        state = ebsco_source_work_state(work_id)
        version = int(dateutil.parser.parse(state.source_modified_time).timestamp())
        return DeletedSourceWork(
            version=version,
            deleted_reason=DeletedReason(
                type="DeletedFromSource", info="not found in EBSCO source"
            ),
            state=state,
        )

    def _transform_visible(
        self, work_id: str, content: str
    ) -> Generator[VisibleSourceWork]:
        marc_records: list[Record] = []
        try:
            marc_records = parse_xml_to_array(io.StringIO(content))
            assert len(marc_records) == 1
        except Exception as e:
            self._add_error(e, "parse", work_id)

        try:
            for record in marc_records:
                yield transform_record(record)
        except Exception as e:
            self._add_error(e, "transform", work_id)

    def transform(self, rows: Iterable[dict[str, Any]]) -> Generator[SourceWork]:
        for row in rows:
            work_id, content = row["id"], row.get("content")

            if not content:
                yield self._transform_deleted(work_id)
            else:
                yield from self._transform_visible(work_id, content)
