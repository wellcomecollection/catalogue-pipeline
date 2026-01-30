from collections.abc import Generator
from typing import Any

import structlog

from adapters.utils.adapter_store import AdapterStore

from .base_transformer import BaseSource

logger = structlog.get_logger(__name__)


class AdapterStoreSource(BaseSource):
    def __init__(self, adapter_store: AdapterStore, changeset_ids: list[str]):
        self.adapter_store = adapter_store
        self.changeset_ids = changeset_ids

    def stream_raw(self) -> Generator[dict[str, Any]]:
        if self.changeset_ids:
            for changeset_id in self.changeset_ids:
                table = self.adapter_store.get_records_by_changeset(changeset_id)
                yield from table.to_pylist()
        else:
            logger.info("No changeset_id provided; performing full reindex of records.")
            table = self.adapter_store.get_all_records()
            yield from table.to_pylist()
