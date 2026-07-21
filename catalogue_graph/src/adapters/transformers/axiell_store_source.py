from collections.abc import Generator
from typing import Any, cast

import pyarrow as pa
import structlog
from pyiceberg.expressions import In

from adapters.transformers.adapter_store_source import AdapterStoreSource
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore

logger = structlog.get_logger(__name__)


class AxiellStoreSource(AdapterStoreSource):
    """AdapterStoreSource that follows adapter rows with deletion facts.

    Fact rows carry a `guid` key (the superseded guid), which adapter rows
    never do; the transformer discriminates on that key. Facts are only read
    for incremental runs: a full reindex writes into an empty index, so
    historic deletions have nothing to overwrite.

    Facts capture detection-time state but live forever, so each is
    re-checked against the current reconciler mappings: a fact whose guid has
    since been reclaimed (revert, handoff, or a redrive of an old changeset)
    is skipped, as its tombstone would overwrite the live work now indexed
    under that guid.
    """

    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None = None,
        facts_store: DeletionFactsStore | None = None,
        reconciler_store: ReconcilerStore | None = None,
    ):
        if (facts_store is None) != (reconciler_store is None):
            raise ValueError(
                "facts_store and reconciler_store must be provided together: "
                "facts cannot be delivered without the reclaimed-guid check"
            )
        super().__init__(adapter_store, changeset_ids, snapshot_id)
        self.facts_store = facts_store
        self.reconciler_store = reconciler_store

    def stream_raw(self) -> Generator[dict[str, Any]]:
        yield from super().stream_raw()

        if self.changeset_ids and self.facts_store is not None:
            # The facts store is read at its own current snapshot:
            # `self.snapshot_id` pins the *adapter* store and is not a valid
            # snapshot of the facts table.
            facts = self.facts_store.get_records_by_changesets(self.changeset_ids)
            reclaimed_guids = self._reclaimed_guids(facts)
            for batch in facts.to_batches():
                for row in batch.to_pylist():
                    if row["guid"] in reclaimed_guids:
                        logger.warning(
                            "Skipped stale deletion fact: guid is an active mapping",
                            fact_id=row["id"],
                            guid=row["guid"],
                        )
                        continue
                    yield row

    def _reclaimed_guids(self, facts: pa.Table) -> set[str]:
        """Fact guids that are currently active reconciler mappings again."""
        if facts.num_rows == 0:
            return set()
        assert self.reconciler_store is not None
        guids = cast(list[str], facts.column("guid").to_pylist())
        mapped = self.reconciler_store.get_namespace_records(In("guid", guids))
        return set(cast(list[str], mapped.column("guid").to_pylist()))
