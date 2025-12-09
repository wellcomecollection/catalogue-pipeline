from collections.abc import Generator, Iterable
from typing import Any

from adapters.utils.adapter_store import AdapterStore
from models.pipeline.source.work import SourceWork

from .base_transformer import BaseTransformer
from .pa_source import PyArrowSource


class AxiellTransformer(BaseTransformer):
    def __init__(self, adapter_store: AdapterStore, changeset_ids: list[str]) -> None:
        super().__init__()
        self.source = PyArrowSource(adapter_store, changeset_ids)

    def transform(self, rows: Iterable[dict[str, Any]]) -> Generator[SourceWork]:
        for _row in rows:
            pass

        # TODO: Implement Axiell transformer
        yield from []
