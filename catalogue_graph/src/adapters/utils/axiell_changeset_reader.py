"""Consumer-facing reader for the Axiell adapter's changeset output.

The adapter's completed event carries changeset ids; the data lives in three
Iceberg tables. This reader is the one place that knows how to turn those ids
into the two streams a consumer needs: adapter rows (upserts and source
tombstones) and superseded-guid deletions. It owns the invariants a consumer
must not reassemble by hand: the facts and reconciler stores travel together,
deletions are filtered through the reclaimed-guid check, and facts are only
read for incremental runs.
"""

from collections.abc import Generator
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Protocol, cast

import pyarrow as pa
import structlog
from pyiceberg.expressions import In
from pyiceberg.table import Table as IcebergTable

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.adapter_store_source import AdapterStoreSource
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore

logger = structlog.get_logger(__name__)


@dataclass(frozen=True)
class SupersededGuid:
    """A deletion fact: `record_id` moved off `guid` in `changeset_id`.

    `guid` is the MARC 001 the record moved away from, and equals the
    source_id the FOLIO sync keys its hrids on.
    """

    fact_id: str
    record_id: str
    guid: str
    changeset_id: str
    last_modified: datetime


class AxiellReaderTableBuilder(Protocol):
    """The subset of an adapter runtime config needed to build reader tables."""

    @property
    def adapter_namespace(self) -> str: ...

    def build_adapter_table(
        self, *, use_rest_api_table: bool = ..., create_if_not_exists: bool = ...
    ) -> IcebergTable: ...

    def build_deletion_facts_table(
        self, *, use_rest_api_table: bool = ..., create_if_not_exists: bool = ...
    ) -> IcebergTable: ...

    def build_reconciler_table(
        self, *, use_rest_api_table: bool = ..., create_if_not_exists: bool = ...
    ) -> IcebergTable: ...


class AxiellChangesetReader:
    """Read one changeset batch's records and deletions.

    The facts and reconciler stores travel together or not at all: a fact can
    only be delivered after the reclaimed-guid check against the current
    mappings, so reading facts without the reconciler store is a correctness
    bug, not a lighter mode.
    """

    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None = None,
        facts_store: DeletionFactsStore | None = None,
        reconciler_store: ReconcilerStore | None = None,
    ) -> None:
        if (facts_store is None) != (reconciler_store is None):
            raise ValueError(
                "facts_store and reconciler_store must be provided together: "
                "facts cannot be delivered without the reclaimed-guid check"
            )
        self.adapter_store = adapter_store
        self.changeset_ids = changeset_ids
        self.snapshot_id = snapshot_id
        self.facts_store = facts_store
        self.reconciler_store = reconciler_store

    @classmethod
    def build(
        cls,
        config: AxiellReaderTableBuilder,
        changeset_ids: list[str],
        *,
        use_rest_api_table: bool,
        snapshot_id: int | None = None,
        adapter_store: AdapterStore | None = None,
        with_deletion_facts: bool = True,
    ) -> "AxiellChangesetReader":
        """Build a reader from an adapter runtime config.

        Facts only apply to incremental runs: a full reindex (no changesets)
        writes into an empty index, so historic deletions have nothing to
        overwrite. The facts and reconciler tables are only read, never
        created; a missing table raises NoSuchTableError rather than silently
        skipping deletion delivery (on a fresh environment, run the reconcile
        step once first). Pass `with_deletion_facts=False` for consumers that
        do not handle deletions yet and should not depend on those tables.
        """
        namespace = config.adapter_namespace
        if adapter_store is None:
            adapter_table = config.build_adapter_table(
                use_rest_api_table=use_rest_api_table, create_if_not_exists=False
            )
            adapter_store = AdapterStore(adapter_table, namespace=namespace)

        facts_store, reconciler_store = None, None
        if changeset_ids and with_deletion_facts:
            facts_table = config.build_deletion_facts_table(
                use_rest_api_table=use_rest_api_table,
                create_if_not_exists=False,
            )
            reconciler_table = config.build_reconciler_table(
                use_rest_api_table=use_rest_api_table,
                create_if_not_exists=False,
            )
            facts_store = DeletionFactsStore(facts_table, namespace=namespace)
            reconciler_store = ReconcilerStore(reconciler_table, namespace=namespace)

        return cls(
            adapter_store,
            changeset_ids,
            snapshot_id,
            facts_store=facts_store,
            reconciler_store=reconciler_store,
        )

    def iter_records(self) -> Generator[dict[str, Any]]:
        """Yield adapter rows for the changesets, unchanged.

        Includes soft-deleted rows (`deleted=True` with content preserved):
        the pipeline transformer needs them to overwrite documents, and the
        FOLIO sync treats them as advisory. With NO changeset ids this is a
        lazily-streamed full scan of every active record (the transformer's
        reindex mode) — consumers that only want a sample must break early.
        """
        source = AdapterStoreSource(
            self.adapter_store, self.changeset_ids, self.snapshot_id
        )
        yield from source.stream_raw()

    def iter_deletions(self) -> Generator[SupersededGuid]:
        """Yield the changesets' deletion facts that are still deliverable.

        Facts capture detection-time state but live forever, so each is
        re-checked against the current reconciler mappings: a fact whose guid
        has since been reclaimed (revert, handoff, or a redrive of an old
        changeset) is skipped, as acting on it would target the live record
        now identified by that guid. Yields nothing for non-incremental runs;
        raises if the reader was built without deletion facts, so a consumer
        wired to a records-only reader fails loudly instead of silently
        delivering nothing.
        """
        if not self.changeset_ids:
            return
        if self.facts_store is None:
            raise RuntimeError(
                "this reader was built without deletion facts "
                "(with_deletion_facts=False or no facts/reconciler stores); "
                "rebuild it with deletion facts to consume deletions"
            )

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
                yield SupersededGuid(
                    fact_id=row["id"],
                    record_id=row["record_id"],
                    guid=row["guid"],
                    changeset_id=row["changeset"],
                    last_modified=row["last_modified"],
                )

    def _reclaimed_guids(self, facts: pa.Table) -> set[str]:
        """Fact guids that are currently active reconciler mappings again."""
        if facts.num_rows == 0:
            return set()
        assert self.reconciler_store is not None
        guids = cast(list[str], facts.column("guid").to_pylist())
        mapped = self.reconciler_store.get_namespace_records(In("guid", guids))
        return set(cast(list[str], mapped.column("guid").to_pylist()))
