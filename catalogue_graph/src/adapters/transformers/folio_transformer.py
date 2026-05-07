import itertools
from collections.abc import Generator, Iterable
from datetime import datetime
from typing import Any

from pymarc.record import Record
from models.pipeline.work_data import WorkData

from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from models.pipeline.identifier import Id
from models.pipeline.source.work import SourceWork, VisibleSourceWork


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
            snapshot_id=snapshot_id,
        )

    def transform_record(
        self, work_id: str, marc_record: Record, source_modified_time: datetime
    ) -> VisibleSourceWork:
        work_data = WorkData()
        work_state = self.source_work_state(
            id_value=work_id,
            source_modified_time=source_modified_time,
        )

        return VisibleSourceWork(
            version=int(
                source_modified_time.timestamp()
            ),
            state=work_state,
            data=work_data,
        )

    # TODO: Remove this override once transform_record is implemented
    def transform(
        self, rows: Iterable[dict[str, Any]]
    ) -> Generator[tuple[str, SourceWork]]:
        return super().transform(itertools.islice(rows, 10))
