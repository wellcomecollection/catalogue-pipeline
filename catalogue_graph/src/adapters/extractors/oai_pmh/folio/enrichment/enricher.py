"""Populate the FOLIO items store from a bib changeset.

The enricher is the new "Run enrichment" step that sits between the loader and the
publish event. Given the bib store ids that changed in a harvest window, it fetches
their items from mod-inventory-storage and upserts them into the ``folio_items_table``
store.

The bib changeset is the trigger: because the FOLIO adapter harvests
``marc21_withholdings``, an item or holdings change advances the instance's OAI
datestamp and so re-appears in the bib window (verified read-only against the live
dev feed).

Identifier keying: the bib store row ``id`` is the full OAI identifier
(``oai:<host>:<set>/<instance-uuid>``), but ``enrichedInstances`` is queried by the
**bare instance UUID** and returns results keyed by that UUID. So the enricher extracts
the UUID for the API call, then re-keys each result back to the originating bib store
id before writing — so the items row ``id`` matches the bib row ``id`` and the
transform-time join lines up.

The store reuses ``AdapterStore`` (the same schema as the bib store): ``id`` is the bib
store id and ``content`` is the enriched items JSON. ``incremental_update`` makes the
load idempotent — an unchanged instance produces no write, and a full replay does not
duplicate rows.

See https://github.com/wellcomecollection/catalogue-pipeline/pull/3438 for the design.
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


def instance_uuid_from_store_id(store_id: str) -> str:
    """Extract the bare FOLIO instance UUID from a bib store id.

    The bib store id is the OAI identifier ``oai:<host>:<set>/<instance-uuid>``; the
    enrichment API wants the trailing UUID. Ids that are already bare (no ``/``) pass
    through unchanged.
    """
    return store_id.rsplit("/", 1)[-1]


def build_item_store_rows(
    items_by_store_id: dict[str, FolioEnrichedInstance],
    namespace: str,
    last_modified: datetime | None = None,
) -> pa.Table:
    """Build an adapter-store Arrow table, one row per bib store id.

    ``id`` is the bib store id (so it joins onto the bib row), ``content`` is the
    serialised enriched-items payload.
    """
    timestamp = last_modified or datetime.now(UTC)
    rows = [
        {
            "namespace": namespace,
            "id": store_id,
            "content": instance.to_store_content(),
            "changeset": None,
            "last_modified": timestamp,
            "deleted": False,
        }
        for store_id, instance in items_by_store_id.items()
    ]
    return pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)


class FolioItemEnricher:
    """Upserts enriched FOLIO items into the items store for a set of bib store ids."""

    def __init__(
        self,
        inventory_client: FolioInventoryClient,
        items_store: AdapterStore,
    ) -> None:
        self._inventory_client = inventory_client
        self._items_store = items_store

    def enrich(self, store_ids: list[str]) -> PipelineStoreUpdate | None:
        """Fetch and upsert items for the given bib store ids.

        ``store_ids`` are bib store ids (OAI identifiers). Returns the store update
        (carrying the items changeset id) or ``None`` when there is nothing to write.
        """
        if not store_ids:
            logger.info("No instance ids to enrich; skipping")
            return None

        # Map bare instance UUID -> bib store id so results can be re-keyed.
        uuid_to_store_id = {
            instance_uuid_from_store_id(store_id): store_id for store_id in store_ids
        }
        enriched = self._inventory_client.enriched_instances(list(uuid_to_store_id))
        if not enriched:
            logger.info("Enrichment returned no instances", requested=len(store_ids))
            return None

        items_by_store_id: dict[str, FolioEnrichedInstance] = {}
        for instance in enriched:
            store_id = uuid_to_store_id.get(instance.instance_id)
            if store_id is None:
                # A returned instance we cannot match back to a requested id cannot be
                # joined onto a bib row. Treat it as a critical contract violation and
                # fail the whole batch rather than silently drop or store an orphan row.
                raise ValueError(
                    "Enriched instance id matched no requested id: "
                    f"{instance.instance_id!r}"
                )
            items_by_store_id[store_id] = instance
        rows = build_item_store_rows(
            items_by_store_id, namespace=self._items_store.namespace
        )
        update = self._items_store.incremental_update(rows)

        logger.info(
            "Enriched FOLIO items",
            requested=len(store_ids),
            fetched=len(enriched),
            changeset_id=update.changeset_id if update else None,
            inserted=len(update.inserted_record_ids) if update else 0,
            updated=len(update.updated_record_ids) if update else 0,
        )
        return update
