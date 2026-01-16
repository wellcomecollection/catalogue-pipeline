from datetime import datetime

from pymarc.record import Record

from adapters.axiell.transformers.other_identifiers import extract_other_identifiers
from adapters.marc.transformers.alternative_titles import extract_alternative_titles
from adapters.marc.transformers.last_transaction_time import (
    extract_last_transaction_time_to_datetime,
)
from adapters.marc.transformers.notes import extract_notes
from adapters.marc.transformers.title import extract_title
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from ingestor.models.shared.invisible_reason import InvisibleReason
from models.pipeline.identifier import Id
from models.pipeline.source.work import InvisibleSourceWork
from models.pipeline.work_data import WorkData


class AxiellTransformer(MarcXmlTransformer):
    def __init__(self, adapter_store: AdapterStore, changeset_ids: list[str]) -> None:
        super().__init__(adapter_store, changeset_ids, Id(id="axiell-guid"))

    def transform_record(
        self, work_id: str, marc_record: Record, source_modified_time: datetime
    ) -> InvisibleSourceWork:
        work_data = WorkData(
            title=extract_title(marc_record),
            alternative_titles=extract_alternative_titles(marc_record),
            other_identifiers=extract_other_identifiers(marc_record),
            notes=extract_notes(marc_record),
        )

        work_state = self.source_work_state(
            id_value=work_id,
            source_modified_time=extract_last_transaction_time_to_datetime(marc_record),
        )

        return InvisibleSourceWork(
            version=int(source_modified_time.timestamp()),
            state=work_state,
            data=work_data,
            invisibility_reasons=[InvisibleReason(type="MimsyWorksAreNotVisible")],
        )
