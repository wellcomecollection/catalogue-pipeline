from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore

from .folio_record_transformer import FolioRecordTransformer
from .marcxml_record_transformer import MarcXMLRecordTransformer


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
            snapshot_id=snapshot_id,
        )

    @property
    def record_transformer(self) -> type[MarcXMLRecordTransformer]:
        return FolioRecordTransformer
