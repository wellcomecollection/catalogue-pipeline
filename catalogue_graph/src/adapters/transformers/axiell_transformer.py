from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from adapters.transformers.marcxml_transformer import MarcXmlTransformer
from adapters.utils.adapter_store import AdapterStore


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
    def work_builder(self) -> type[AxiellWorkBuilder]:
        return AxiellWorkBuilder
