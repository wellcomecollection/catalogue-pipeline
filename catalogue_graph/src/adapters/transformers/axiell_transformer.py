from datetime import datetime

from pymarc.record import Record

from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from models.pipeline.source.work import InvisibleSourceWork

from .axiell_record_transformer import AxiellRecordTransformer


class AxiellTransformer(MarcXmlTransformer):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None,
    ) -> None:
        super().__init__(
            adapter_store=adapter_store,
            changeset_ids=changeset_ids,
            snapshot_id=snapshot_id,
        )

    @property
    def record_transformer(self) -> type[AxiellRecordTransformer]:
        return AxiellRecordTransformer

    def transform_record(
        self, marc_record: Record, last_modified: datetime
    ) -> InvisibleSourceWork:
        record_transformer = self.record_transformer(marc_record, last_modified)
        # TODO: Why is this work invisible?
        return record_transformer.invisible_work
