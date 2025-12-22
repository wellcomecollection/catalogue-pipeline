from __future__ import annotations

from datetime import datetime

from pymarc.record import Record

from adapters.marc.transformers.identifier import extract_id
from adapters.marc.transformers.title import extract_title
from adapters.transformers.base_transformer import BaseTransformer
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from models.pipeline.identifier import Id
from models.pipeline.source.work import VisibleSourceWork
from models.pipeline.work_data import WorkData


class MarcFieldTransformerForTests(MarcXmlTransformer):
    """A lightweight MarcXmlTransformer subclass for unit-testing MARC field extractors.

    The production MarcXmlTransformer requires an AdapterStore; for unit tests we only
    need the record-level transformation methods.

    Extend this class (or add fields here) as we add tests for more MARC extractors.
    """

    def __init__(self) -> None:
        # Avoid MarcXmlTransformer.__init__ (requires AdapterStore) but keep BaseTransformer
        # initialisation so error tracking behaves normally.
        BaseTransformer.__init__(self)
        self.identifier_type = Id(id="marc-test")

    def transform_record(
        self, marc_record: Record, source_modified_time: datetime
    ) -> VisibleSourceWork:
        work_id = extract_id(marc_record)
        work_data = WorkData(title=extract_title(marc_record))

        work_state = self.source_work_state(
            id_value=work_id, source_modified_time=source_modified_time
        )

        return VisibleSourceWork(
            version=int(source_modified_time.timestamp()),
            state=work_state,
            data=work_data,
        )
