from collections.abc import Generator
from typing import Any

import structlog

from adapters.utils.adapter_store import AdapterStore
from core.source import BaseSource

logger = structlog.get_logger(__name__)

# Measured on the production FOLIO table (1.7M rows): for small changesets an
# In("id", ...) content read prunes to the few data files overlapping the id
# range (~1.5 GB peak vs ~1.8 GB for the un-prunable changeset scan, and
# seconds vs a full-table read). For large changesets id-filtered reads
# re-read the same overlapping files repeatedly and can exceed the Lambda
# timeout, so we fall back to the eager changeset read.
SMALL_CHANGESET_THRESHOLD = 1000


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
            yield from self._stream_changeset_records()
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

    def _stream_changeset_records(self) -> Generator[dict[str, Any]]:
        # Resolve the snapshot once so the id lookup and the content read see
        # identical rows even when the caller did not pin a snapshot.
        snapshot_id = self.snapshot_id
        if snapshot_id is None:
            snapshot_id = self.adapter_store.current_snapshot_id()

        ids, min_last_modified = self.adapter_store.get_changeset_record_ids(
            self.changeset_ids, snapshot_id
        )
        if not ids:
            return
        if len(ids) <= SMALL_CHANGESET_THRESHOLD:
            # Changeset reads include soft-deleted rows (needed to overwrite
            # documents downstream); get_records_by_ids preserves that. The
            # last_modified bound is derived from the changeset's own rows, so
            # it excludes nothing and lets file stats skip old compacted files.
            table = self.adapter_store.get_records_by_ids(
                ids, snapshot_id, updated_since=min_last_modified
            )
            yield from table.to_pylist()
        else:
            for changeset_id in self.changeset_ids:
                table = self.adapter_store.get_records_by_changeset(
                    changeset_id, snapshot_id
                )
                yield from table.to_pylist()
