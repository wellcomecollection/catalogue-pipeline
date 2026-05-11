from datetime import datetime

from pymarc.record import Record

from adapters.transformers.folio.predecessor_identifier import extract_predecessor_id
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from models.pipeline.identifier import Id
from models.pipeline.source.work import VisibleSourceWork
from models.pipeline.work_data import WorkData


class FolioTransformer(MarcXmlTransformer):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None,
    ) -> None:
        super().__init__(
            adapter_store,
            changeset_ids=changeset_ids,
            identifier_type=Id(id="folio-instance"),
            predecessor_identifier_type=Id(id="sierra-system-number"),
            snapshot_id=snapshot_id,
        )

    def extract_predecessor_id(self, marc_record: Record) -> str | None:
        return extract_predecessor_id(marc_record)

    def transform_record(
        self, marc_record: Record, source_modified_time: datetime
    ) -> VisibleSourceWork:
        work_data = WorkData()
        work_state = self.source_work_state(
            marc_record=marc_record,
            source_modified_time=source_modified_time,
        )

        return VisibleSourceWork(
            version=int(source_modified_time.timestamp()),
            state=work_state,
            data=work_data,
        )
