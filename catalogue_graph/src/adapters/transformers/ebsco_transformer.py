import io
from collections.abc import Generator
from typing import Any

import dateutil.parser
from ingestor.models.shared.deleted_reason import DeletedReason
from models.pipeline.source.work import DeletedSourceWork, SourceWork, VisibleSourceWork
from pydantic import BaseModel
from pymarc import parse_xml_to_array

from adapters.ebsco.models.marc import MarcRecord
from adapters.ebsco.transformers.ebsco_to_weco import (
    ebsco_source_work_state,
    transform_record,
)
from adapters.utils.adapter_store import AdapterStore

from .base_transformer import BaseTransformer
from .pa_source import PyArrowSource


class TransformationError(BaseModel):
    work_id: str | None
    stage: str
    detail: str


class EbscoTransformer(BaseTransformer):
    def __init__(self, adapter_store: AdapterStore, changeset_ids: list[str]) -> None:
        self.source = PyArrowSource(adapter_store, changeset_ids)

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

    @staticmethod
    def _transform_visible(
        work_id: str, content: str
    ) -> Generator[VisibleSourceWork, TransformationError]:
        """Parse a MARC XML string into SourceWork records."""
        try:
            marc_records: list[MarcRecord] = parse_xml_to_array(io.StringIO(content))
        except Exception as e:
            yield TransformationError(
                stage="transform", id=work_id, reason="parse_error", detail=str(e)[:500]
            )
            return

        assert len(marc_records) == 1

        for record in marc_records:
            try:
                yield transform_record(record)
            except Exception as e:
                yield TransformationError(
                    stage="transform",
                    id=work_id,
                    reason="transform_error",
                    detail=str(e)[:500],
                )

    def transform(
        self, row: dict[str, Any]
    ) -> Generator[SourceWork | TransformationError]:
        work_id = row["id"]
        content = row.get("content")
        
        if not content:
            yield self._transform_deleted(work_id)
        else:
            yield from self._transform_visible(work_id, content)
