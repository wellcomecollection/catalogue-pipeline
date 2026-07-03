from collections.abc import Generator
from typing import Any

import structlog

from adapters.utils.adapter_store import AdapterStore
from core.source import BaseSource

logger = structlog.get_logger(__name__)


class AdapterStoreSource(BaseSource):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None = None,
    ):
        self.adapter_store = adapter_store
        self.changeset_ids = changeset_ids
        self.snapshot_id = snapshot_id

    def stream_raw(self) -> Generator[dict[str, Any]]:
        if self.changeset_ids:
            # Includes soft-deleted rows, needed to overwrite documents downstream.
            # Convert one batch at a time so the Python dicts never hold the
            # whole (possibly multi-changeset) table alongside the Arrow copy.
            table = self.adapter_store.get_records_by_changesets(
                self.changeset_ids, self.snapshot_id
            )
            for batch in table.to_batches():
                yield from batch.to_pylist()
        else:
            logger.info("No changeset_id provided; performing full reindex of records.")

            # During a full reindex we are writing into an empty index,
            # so no need to include deleted rows to overwrite documents.
            # Stream record batches so the full table need not be materialised
            # at once. Close the reader on exit so an abandoned stream (e.g. a
            # consumer error mid-reindex) does not leave prefetch reads running.
            batches = self.adapter_store.stream_active_namespace_records(
                self.snapshot_id
            )
            try:
                for batch in batches:
                    yield from batch.to_pylist()
            finally:
                batches.close()
