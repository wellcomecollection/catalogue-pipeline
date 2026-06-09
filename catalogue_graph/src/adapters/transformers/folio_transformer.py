from adapters.transformers.builders.folio_work_builder import FolioWorkBuilder
from adapters.transformers.builders.marc_xml_work_builder import MarcXmlWorkBuilder
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore


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
    def record_transformer(self) -> type[MarcXmlWorkBuilder]:
        return FolioWorkBuilder
