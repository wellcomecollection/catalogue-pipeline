from __future__ import annotations

from collections.abc import Callable
from datetime import datetime

from pymarc.record import Record

from adapters.marc.transformers.identifier import extract_id
from adapters.marc.transformers.title import extract_title
from adapters.transformers.base_transformer import BaseTransformer
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from models.pipeline.identifier import Id
from models.pipeline.source.work import VisibleSourceWork
from models.pipeline.work_data import WorkData
from models.pipeline.work_state import WorkRelations


class MarcXmlTransformerForTests(MarcXmlTransformer):
    """A lightweight MarcXmlTransformer subclass for unit-testing field extractors.

    The production MarcXmlTransformer requires an AdapterStore; in tests we only need
    record-level transformation plus the consistent WorkState behaviour.

    This transformer always uses `extract_id()` for the source identifier, and delegates
    building the `WorkData` to the supplied callable.
    """

    def __init__(
        self,
        build_work_data: Callable[[Record], WorkData],
        build_relations: Callable[[Record], WorkRelations | None] | None = None,
    ) -> None:
        # Avoid MarcXmlTransformer.__init__ (requires AdapterStore) but keep BaseTransformer
        # initialisation so error tracking behaves normally.
        BaseTransformer.__init__(self)
        self.identifier_type = Id(id="marc-test")
        self._build_work_data = build_work_data
        self._build_relations = build_relations

    def transform_record(
        self, marc_record: Record, source_modified_time: datetime
    ) -> VisibleSourceWork:
        work_id = extract_id(marc_record)
        work_data = self._build_work_data(marc_record)

        relations: WorkRelations | None = None
        if self._build_relations is not None:
            relations = self._build_relations(marc_record)

        work_state = self.source_work_state(
            id_value=work_id,
            source_modified_time=source_modified_time,
            relations=relations,
        )

        return VisibleSourceWork(
            version=int(source_modified_time.timestamp()),
            state=work_state,
            data=work_data,
        )


class MarcFieldTransformerForTests(MarcXmlTransformerForTests):
    """Backwards-compatible helper used by existing MARC title tests."""

    def __init__(self) -> None:
        super().__init__(build_work_data=lambda r: WorkData(title=extract_title(r)))
