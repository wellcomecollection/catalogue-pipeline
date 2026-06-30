"""Populate the FOLIO items store from a bib changeset.

The enricher is the new "Run enrichment" step that sits between the loader and the
publish event. Given the set of instance ids that changed in a bib harvest window,
it fetches their items/holdings from mod-inventory-storage and upserts them into the
``folio_items_table`` store, keyed by instance id.

The bib changeset is the trigger: because the FOLIO adapter harvests
``marc21_withholdings``, an item or holdings change advances the instance's OAI
datestamp and so re-appears in the bib window (verified read-only against the live
dev feed).

See https://github.com/wellcomecollection/catalogue-pipeline/pull/3438 for the design.

The store reuses ``AdapterStore`` (the same schema as the bib store): ``id`` is the
instance id and ``content`` is the enriched items/holdings JSON. Using
``incremental_update`` makes the load idempotent — an unchanged instance produces no
write, and a full replay does not duplicate rows.
"""

from __future__ import annotations

from datetime import UTC, datetime

import pyarrow as pa
import structlog

from adapters.extractors.oai_pmh.folio.enrichment.inventory_client import (
    FolioInventoryClient,
)
from adapters.extractors.oai_pmh.folio.enrichment.models import FolioEnrichedInstance
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.pipeline_store import PipelineStoreUpdate
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA

logger = structlog.get_logger(__name__)


def build_item_store_rows(
    enriched: list[FolioEnrichedInstance],
    namespace: str,
    last_modified: datetime | None = None,
) -> pa.Table:
    """Build an adapter-store Arrow table from enriched instances.

    One row per instance: ``id`` is the instance id, ``content`` is the serialised
    items/holdings payload.
    """
    timestamp = last_modified or datetime.now(UTC)
    rows = [
        {
            "namespace": namespace,
            "id": instance.instance_id,
            "content": instance.to_store_content(),
            "changeset": None,
            "last_modified": timestamp,
            "deleted": False,
        }
        for instance in enriched
    ]
    return pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)


class FolioItemEnricher:
    """Upserts enriched FOLIO items into the items store for a set of instance ids."""

    def __init__(
        self,
        inventory_client: FolioInventoryClient,
        items_store: AdapterStore,
    ) -> None:
        self._inventory_client = inventory_client
        self._items_store = items_store

    def enrich(self, instance_ids: list[str]) -> PipelineStoreUpdate | None:
        """Fetch and upsert items for ``instance_ids``.

        Returns the store update (carrying the items changeset id) or ``None`` when
        there is nothing to write.
        """
        if not instance_ids:
            logger.info("No instance ids to enrich; skipping")
            return None

        enriched = self._inventory_client.enriched_instances(instance_ids)
        if not enriched:
            logger.info("Enrichment returned no instances", requested=len(instance_ids))
            return None

        rows = build_item_store_rows(enriched, namespace=self._items_store.namespace)
        update = self._items_store.incremental_update(rows)

        logger.info(
            "Enriched FOLIO items",
            requested=len(instance_ids),
            fetched=len(enriched),
            changeset_id=update.changeset_id if update else None,
            inserted=len(update.inserted_record_ids) if update else 0,
            updated=len(update.updated_record_ids) if update else 0,
        )
        return update
