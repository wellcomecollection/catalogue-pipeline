"""Populate the FOLIO inventory store from a bib changeset.

The enricher is the "Run enrichment" step that sits between the loader and the
publish event. Given the bib store ids that changed in a harvest window, it fetches
their items and holdings from mod-inventory-storage and upserts them into the
``folio_inventory_table`` store.

The bib changeset is the trigger: because the FOLIO adapter harvests
``marc21_withholdings``, an item or holdings change advances the instance's OAI
datestamp and so re-appears in the bib window (verified read-only against the live
dev feed).

Identifier keying: the bib store row ``id`` is the full OAI identifier
(``oai:<host>:<set>/<instance-uuid>``), but ``items-and-holdings`` is queried by the
**bare instance UUID** and returns results keyed by that UUID. So the enricher extracts
the UUID for the API call, then re-keys each result back to the originating bib store
id before writing — so the items row ``id`` matches the bib row ``id`` and the
transform-time join lines up.

The store reuses ``AdapterStore`` (the same schema as the bib store): ``id`` is the bib
store id and ``content`` is the instance's raw items-and-holdings record, kept whole
so later mappings can read any field without a re-fetch. ``incremental_update`` makes the
load idempotent — an unchanged instance produces no write, and a full replay does not
duplicate rows.

See https://github.com/wellcomecollection/catalogue-pipeline/pull/3438 for the design.
"""

from __future__ import annotations

from datetime import UTC, datetime
from typing import NamedTuple

import pyarrow as pa
import structlog

from adapters.extractors.oai_pmh.folio.enrichment.inventory_client import (
    FolioInventoryClient,
)
from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioInventoryRecord,
    RejectedInventoryRecord,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.pipeline_store import PipelineStoreUpdate
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA

logger = structlog.get_logger(__name__)

# Rejecting more than this many records, or this share of a large fetch, looks like
# a systemic break (a FOLIO change, say) rather than a few bad records.
REJECTED_FLOOR = 10
REJECTED_SHARE = 0.01


class SystemicRejectionError(ValueError):
    """Too many records rejected in one fetch; carries them so they can be reported."""

    def __init__(self, rejected: list[RejectedInventoryRecord], total: int) -> None:
        self.rejected = rejected
        self.total = total
        super().__init__(
            f"Rejected {len(rejected)} of {total} FOLIO inventory records, "
            "which looks like a systemic problem rather than a few bad records"
        )


class InventoryRows(NamedTuple):
    rows: pa.Table | None
    rejected: list[RejectedInventoryRecord]


class EnrichmentResult(NamedTuple):
    update: PipelineStoreUpdate | None
    rejected: list[RejectedInventoryRecord]


def instance_uuid_from_store_id(store_id: str) -> str:
    """Extract the bare FOLIO instance UUID from a bib store id.

    The bib store id is the OAI identifier ``oai:<host>:<set>/<instance-uuid>``; the
    enrichment API wants the trailing UUID. Ids that are already bare (no ``/``) pass
    through unchanged.
    """
    return store_id.rsplit("/", 1)[-1]


def build_inventory_store_rows(
    records_by_store_id: dict[str, FolioInventoryRecord],
    namespace: str,
    last_modified: datetime | None = None,
) -> pa.Table:
    """Build an adapter-store Arrow table, one row per bib store id.

    ``id`` is the bib store id (so it joins onto the bib row), ``content`` is the
    serialised raw record.
    """
    timestamp = last_modified or datetime.now(UTC)
    rows = [
        {
            "namespace": namespace,
            "id": store_id,
            "content": record.to_store_content(),
            "changeset": None,
            "last_modified": timestamp,
            "deleted": False,
        }
        for store_id, record in records_by_store_id.items()
    ]
    return pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)


def fetch_inventory_rows(
    inventory_client: FolioInventoryClient,
    store_ids: list[str],
    namespace: str,
) -> InventoryRows:
    """Fetch items and holdings for the given bib store ids as an adapter-store
    Arrow table, with the records left out. ``rows`` is ``None`` when no record
    can be stored.

    Each bad record is rejected on its own and logged, so it doesn't hold up the
    rest of the batch. Too many rejections fail the call instead.
    """
    uuid_to_store_id = {
        instance_uuid_from_store_id(store_id): store_id for store_id in store_ids
    }
    fetch = inventory_client.items_and_holdings(list(uuid_to_store_id))
    rejected = list(fetch.rejected)

    records_by_store_id: dict[str, FolioInventoryRecord] = {}
    for record in fetch.records:
        store_id = uuid_to_store_id.get(record.instance_id)
        if store_id is None:
            rejected.append(
                RejectedInventoryRecord(
                    instance_id=record.instance_id,
                    reason="instance id matched no requested id",
                )
            )
            continue
        records_by_store_id[store_id] = record

    for r in rejected:
        logger.warning(
            "Rejected FOLIO inventory record",
            instance_id=r.instance_id,
            reason=r.reason,
        )
    total = len(records_by_store_id) + len(rejected)
    if len(rejected) > max(REJECTED_FLOOR, REJECTED_SHARE * total):
        raise SystemicRejectionError(rejected, total)

    if not records_by_store_id:
        return InventoryRows(None, rejected)
    return InventoryRows(
        build_inventory_store_rows(records_by_store_id, namespace=namespace),
        rejected,
    )


class FolioInventoryEnricher:
    """Upserts FOLIO items and holdings into the store for a set of bib store ids."""

    def __init__(
        self,
        inventory_client: FolioInventoryClient,
        inventory_store: AdapterStore,
    ) -> None:
        self._inventory_client = inventory_client
        self._inventory_store = inventory_store

    def enrich(self, store_ids: list[str]) -> EnrichmentResult:
        """Fetch and upsert items and holdings for the given bib store ids.

        ``store_ids`` are bib store ids (OAI identifiers). Returns the store update
        (carrying the items changeset id, or ``None`` when there is nothing to
        write) and the records left out.
        """
        if not store_ids:
            logger.info("No instance ids to enrich; skipping")
            return EnrichmentResult(None, [])

        rows, rejected = fetch_inventory_rows(
            self._inventory_client, store_ids, namespace=self._inventory_store.namespace
        )
        if rows is None:
            logger.info(
                "Enrichment returned no instances to store",
                requested=len(store_ids),
                rejected=len(rejected),
            )
            return EnrichmentResult(None, rejected)

        update = self._inventory_store.incremental_update(rows)

        logger.info(
            "Enriched FOLIO inventory",
            requested=len(store_ids),
            fetched=rows.num_rows,
            changeset_id=update.changeset_id if update else None,
            inserted=len(update.inserted_record_ids) if update else 0,
            updated=len(update.updated_record_ids) if update else 0,
            rejected=len(rejected),
        )
        return EnrichmentResult(update, rejected)
