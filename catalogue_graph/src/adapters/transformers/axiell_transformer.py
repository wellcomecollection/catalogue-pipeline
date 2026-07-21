from collections.abc import Generator
from typing import Any

from adapters.transformers.adapter_store_source import AdapterStoreSource
from adapters.transformers.axiell_store_source import AxiellStoreSource
from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from adapters.transformers.builders.reconciler_work_builder import ReconcilerWorkBuilder
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore
from ingestor.models.shared.deleted_reason import DeletedFromSource
from models.pipeline.source.work import SourceWork


class AxiellTransformer(MarcXmlTransformer):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None,
        facts_store: DeletionFactsStore | None = None,
        reconciler_store: ReconcilerStore | None = None,
    ) -> None:
        # Stored before super().__init__, which builds the source via _build_source.
        self._facts_store = facts_store
        self._reconciler_store = reconciler_store
        super().__init__(
            adapter_store=adapter_store,
            changeset_ids=changeset_ids,
            snapshot_id=snapshot_id,
        )

    def _build_source(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None,
    ) -> AdapterStoreSource:
        return AxiellStoreSource(
            adapter_store,
            changeset_ids,
            snapshot_id,
            facts_store=self._facts_store,
            reconciler_store=self._reconciler_store,
        )

    @property
    def work_builder(self) -> type[AxiellWorkBuilder]:
        return AxiellWorkBuilder

    def _transform_row(self, row: dict[str, Any]) -> Generator[tuple[str, SourceWork]]:
        # Deletion facts (appended by AxiellStoreSource) carry a `guid` key;
        # adapter rows never do. Each fact tombstones its superseded guid.
        if "guid" in row:
            builder = ReconcilerWorkBuilder(row["guid"], row["last_modified"])
            yield (
                row["id"],
                builder.transform_deleted_work(deleted_reason=DeletedFromSource()),
            )
        else:
            yield from super()._transform_row(row)
