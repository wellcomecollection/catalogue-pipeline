from adapters.transformers.adapter_store_source import AdapterStoreSource
from adapters.transformers.builders.folio_work_builder import FolioWorkBuilder
from adapters.transformers.folio_store_source import FolioStoreSource
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
        # Stored before super().__init__, which builds the source via _build_source.
        self._items_store = items_store
        super().__init__(
            adapter_store,
            changeset_ids=changeset_ids,
            snapshot_id=snapshot_id,
        )

    def _build_source(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None,
    ) -> AdapterStoreSource:
        return FolioStoreSource(
            adapter_store, changeset_ids, snapshot_id, items_store=self._items_store
        )

    @property
    def work_builder(self) -> type[FolioWorkBuilder]:
        return FolioWorkBuilder
