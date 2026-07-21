"""Build the store pair backing stateless deletion-facts delivery.

Delivery needs the deletion facts table (the facts to emit) and the
reconciler table (to skip facts whose guid is an active mapping again), so
the two stores travel together or not at all.
"""

from typing import Protocol

from pyiceberg.table import Table as IcebergTable

from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore


class FactsDeliveryTableBuilder(Protocol):
    """The subset of an adapter runtime config needed to build delivery tables."""

    def build_deletion_facts_table(
        self, *, use_rest_api_table: bool = ..., create_if_not_exists: bool = ...
    ) -> IcebergTable: ...

    def build_reconciler_table(
        self, *, use_rest_api_table: bool = ..., create_if_not_exists: bool = ...
    ) -> IcebergTable: ...


def build_facts_delivery_stores(
    config: FactsDeliveryTableBuilder,
    namespace: str,
    changeset_ids: list[str],
    *,
    use_rest_api_table: bool,
) -> tuple[DeletionFactsStore, ReconcilerStore] | None:
    """Return the (facts, reconciler) store pair for delivery, or None.

    Facts only apply to incremental runs: a full reindex (no changesets)
    writes into an empty index, so historic deletions have nothing to
    overwrite. This function only reads the tables; the reconcile step
    creates them. A missing table fails the transform (NoSuchTableError)
    rather than silently skipping deletion delivery. On a fresh environment,
    run the reconcile step once before transforming incrementally.
    """
    if not changeset_ids:
        return None

    facts_table = config.build_deletion_facts_table(
        use_rest_api_table=use_rest_api_table,
        create_if_not_exists=False,
    )
    reconciler_table = config.build_reconciler_table(
        use_rest_api_table=use_rest_api_table,
        create_if_not_exists=False,
    )

    return (
        DeletionFactsStore(facts_table, namespace=namespace),
        ReconcilerStore(reconciler_table, namespace=namespace),
    )
