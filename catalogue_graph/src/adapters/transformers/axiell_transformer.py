from collections.abc import Generator, Iterable
from typing import Any

from adapters.utils.adapter_store import AdapterStore
from models.pipeline.source.work import SourceWork

from .adapter_store_source import AdapterStoreSource
from .base_transformer import BaseTransformer


class AxiellTransformer(BaseTransformer):
    def __init__(self, adapter_store: AdapterStore, changeset_ids: list[str]) -> None:
        super().__init__()
        self.source = AdapterStoreSource(adapter_store, changeset_ids)

    def transform(self, rows: Iterable[dict[str, Any]]) -> Generator[SourceWork]:
        for _row in rows:
            pass

        # TODO: Implement Axiell transformer
        yield from []
