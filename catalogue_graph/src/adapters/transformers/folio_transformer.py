from adapters.transformers.builders.folio_work_builder import FolioWorkBuilder
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore


class FolioTransformer(MarcXmlTransformer):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None,
        items_store: AdapterStore | None = None,
    ) -> None:
        super().__init__(
            adapter_store,
            changeset_ids=changeset_ids,
            snapshot_id=snapshot_id,
            items_store=items_store,
        )

    @property
    def work_builder(self) -> type[FolioWorkBuilder]:
        return FolioWorkBuilder
