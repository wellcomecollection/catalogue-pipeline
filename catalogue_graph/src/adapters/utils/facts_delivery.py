"""Build the store pair backing stateless deletion-facts delivery.

Delivery needs the deletion facts table (the facts to emit) and the
reconciler table (to skip facts whose guid is an active mapping again), so
the two stores travel together or not at all.
"""

from typing import Protocol

import structlog
from pyiceberg.exceptions import NoSuchTableError
from pyiceberg.table import Table as IcebergTable

from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore

logger = structlog.get_logger(__name__)


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
    overwrite. Missing tables are tolerated so the transform still runs
    before the adapter reconcile step has created them (cutover, fresh dev
    environments).
    """
    if not changeset_ids:
        return None

    try:
        facts_table = config.build_deletion_facts_table(
            use_rest_api_table=use_rest_api_table,
            create_if_not_exists=False,
        )
        reconciler_table = config.build_reconciler_table(
            use_rest_api_table=use_rest_api_table,
            create_if_not_exists=False,
        )
    except NoSuchTableError as e:
        logger.warning(
            "Deletion facts or reconciler table not found; "
            "transforming without facts delivery",
            error=str(e),
        )
        return None

    return (
        DeletionFactsStore(facts_table, namespace=namespace),
        ReconcilerStore(reconciler_table, namespace=namespace),
    )
