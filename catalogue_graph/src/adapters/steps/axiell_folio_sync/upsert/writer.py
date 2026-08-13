"""
The create/update write path: turn a MappedPayloads into FOLIO records.

``upsert_from_payloads`` enforces the write order Instance → Holdings → Item,
soft-suppresses the item for deleted records, and best-effort rolls back any
records it created if a later step fails. It is the counterpart to the
guid-cascade reconciler in ``reconcile.py`` (which removes records instead).
"""

from __future__ import annotations

import structlog

from ..folio import FolioInventoryOps, RefCache
from ..mapping import MappedPayloads
from ..results import EntityResult, UpsertError, UpsertResult
from .entities import (
    _best_effort_delete,
    _resolve_item_note_types,
    _suppress_entity,
    _upsert_entity,
)

logger = structlog.get_logger(__name__)


def upsert_from_payloads(
    mapped: MappedPayloads,
    folio: FolioInventoryOps,
    *,
    ref_cache: RefCache | None = None,
    dry_run: bool = False,
) -> UpsertResult:
    """
    Upsert a record using the output of mapping.select_and_build().

    Args:
        mapped:   MappedPayloads model with instance, holdings, item, meta.
        folio:   Authenticated Inventory operations client.
        ref_cache: RefCache instance for resolving note types (optional).
        dry_run:  If True, plan without writing.
    """
    meta = mapped.meta
    source_id: str = meta.source_id
    deleted: bool = meta.deleted
    instance_hrid: str = meta.instance_hrid
    holdings_hrid: str = meta.holdings_hrid
    item_hrid: str = meta.item_hrid

    result = UpsertResult(
        source_id=source_id,
        mapping_version=meta.mapping_version,
    )

    created_instance_id: str | None = None
    created_holdings_id: str | None = None

    try:
        # ── Instance ────────────────────────────────────────────────────────
        action, instance_id = _upsert_entity(
            folio,
            search_path="/inventory/instances",
            list_key="instances",
            write_path_prefix="/inventory/instances",
            hrid=instance_hrid,
            payload=mapped.instance.model_dump(exclude_none=True),
            dry_run=dry_run,
        )
        result.instance = EntityResult(action=action, id=instance_id)
        if action == "create" and not dry_run:
            created_instance_id = instance_id
        if dry_run:
            instance_id = f"dry-run:{instance_hrid}"

        # ── Holdings ────────────────────────────────────────────────────────
        holdings_payload = {
            **mapped.holdings.model_dump(exclude_none=True),
            "instanceId": instance_id,
        }
        action, holdings_id = _upsert_entity(
            folio,
            search_path="/holdings-storage/holdings",
            list_key="holdingsRecords",
            write_path_prefix="/holdings-storage/holdings",
            hrid=holdings_hrid,
            payload=holdings_payload,
            dry_run=dry_run,
        )
        result.holdings = EntityResult(action=action, id=holdings_id)
        if action == "create" and not dry_run:
            created_holdings_id = holdings_id
        if dry_run:
            holdings_id = f"dry-run:{holdings_hrid}"

        # ── Item ────────────────────────────────────────────────────────────
        if deleted:
            result.item = _suppress_entity(
                folio,
                search_path="/inventory/items",
                list_key="items",
                write_path_prefix="/inventory/items",
                hrid=item_hrid,
                dry_run=dry_run,
            )
        else:
            item_payload = {
                **mapped.item.model_dump(exclude_none=True),
                "holdingsRecordId": holdings_id,
            }
            # Resolve item note types if ref_cache is available
            if ref_cache:
                item_payload = _resolve_item_note_types(item_payload, ref_cache)
            action, item_id = _upsert_entity(
                folio,
                search_path="/inventory/items",
                list_key="items",
                write_path_prefix="/inventory/items",
                hrid=item_hrid,
                payload=item_payload,
                dry_run=dry_run,
            )
            result.item = EntityResult(action=action, id=item_id)

    except Exception as exc:
        if not dry_run:
            if created_holdings_id:
                _best_effort_delete(
                    folio,
                    path=f"/holdings-storage/holdings/{created_holdings_id}",
                    source_id=source_id,
                    entity="holdings",
                )
            if created_instance_id:
                _best_effort_delete(
                    folio,
                    path=f"/inventory/instances/{created_instance_id}",
                    source_id=source_id,
                    entity="instance",
                )

        result.errors.append(UpsertError(type="api", detail=str(exc)))
        logger.error("api_error", source_id=source_id, error=str(exc), exc_info=True)

    return result
