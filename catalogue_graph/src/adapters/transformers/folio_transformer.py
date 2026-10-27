from typing import Any

from pymarc.record import Record

from adapters.extractors.oai_pmh.folio.enrichment.models import FolioEnrichedInstance
from adapters.transformers.builders.folio_work_builder import FolioWorkBuilder
from adapters.transformers.folio_store_source import FolioStoreSource
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.adapter_store_source import AdapterStoreSource


class FolioTransformer(MarcXmlTransformer):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None,
        ids: list[str] | None = None,
        inventory_store: AdapterStore | None = None,
    ) -> None:
        # Stored before super().__init__, which builds the source via _build_source.
        self._inventory_store = inventory_store
        super().__init__(
            adapter_store, changeset_ids=changeset_ids, snapshot_id=snapshot_id, ids=ids
        )

    def _build_source(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None,
        ids: list[str] | None = None,
    ) -> AdapterStoreSource:
        return FolioStoreSource(
            adapter_store,
            changeset_ids,
            snapshot_id,
            ids=ids,
            inventory_store=self._inventory_store,
        )

    def _make_builder(
        self, marc_record: Record, row: dict[str, Any]
    ) -> FolioWorkBuilder:
        content = row.get("inventory_content")
        inventory = (
            FolioEnrichedInstance.from_store_content(content) if content else None
        )
        return FolioWorkBuilder(marc_record, row["last_modified"], inventory=inventory)

    @property
    def work_builder(self) -> type[FolioWorkBuilder]:
        return FolioWorkBuilder
