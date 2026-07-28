"""
FOLIO Inventory upsert orchestrator.

Entry point:
  upsert_from_payloads() — takes the dict produced by mapping.build_payloads()

Enforces write order:  Instance → Holdings → Item.
Deleted records are soft-suppressed: discoverySuppress on all three, plus
staffSuppress on the instance (holdings and items have no staffSuppress field).
dry_run=True resolves existing records and plans actions but makes no writes.

Result dict shape
─────────────────
{
    "source_id": "...",
    "instance":  {"action": "create|update|suppress|skip", "id": "..."},
    "holdings":  {"action": "...", "id": "..."},
    "item":      {"action": "...", "id": "..."},
    "errors":    [{"type": "mapping|api", "detail": "..."}],
}
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

import structlog

from .folio_callables import FolioInventoryOps
from .mapping import (
    EntityResult,
    GuidCascadeResult,
    MappedPayloads,
    UpsertError,
    UpsertResult,
    _holdings_hrid,
    _instance_hrid,
    _item_hrid,
)
from .ref_cache import RefCache

logger = structlog.get_logger(__name__)

# Fields that FOLIO returns in GET responses but rejects in PUT/POST bodies.
# Sending them back causes 422 "Unrecognized field" errors.
_READONLY_FIELDS: frozenset[str] = frozenset(
    {
        # Holdings
        "holdingsItems",
        "bareHoldingsItems",
        # Instances
        "holdingsRecords2",
        "precedingTitles",
        "succeedingTitles",
        # Items
        "circulationNotes",
        "lastCheckIn",
    }
)


def _strip_readonly(record: dict) -> dict:
    """Remove computed read-only fields that FOLIO rejects on PUT."""
    return {k: v for k, v in record.items() if k not in _READONLY_FIELDS}


# Write-path prefixes whose entity carries a staffSuppress field. Only FOLIO
# instances do; holdings-storage 422s on it and items silently drop it.
_STAFF_SUPPRESS_PATHS: frozenset[str] = frozenset({"/inventory/instances"})


# ── shared helpers ────────────────────────────────────────────────────────────


def _find_by_hrid(
    folio: FolioInventoryOps, path: str, hrid: str, list_key: str
) -> dict | None:
    """Return the first FOLIO record matching hrid via CQL, or None."""
    try:
        result = folio.get(path, {"query": f'hrid=="{hrid}"', "limit": 1})
        records = result.get(list_key, [])
        return records[0] if records else None
    except Exception as exc:
        logger.warning("hrid lookup failed path=%s hrid=%s error=%s", path, hrid, exc)
        return None


def _suppress_entity(
    folio: FolioInventoryOps,
    *,
    search_path: str,
    list_key: str,
    write_path_prefix: str,
    hrid: str,
    dry_run: bool,
) -> EntityResult:
    """Resolve an entity by hrid and set its suppression flags.

    discoverySuppress is set on every entity; staffSuppress only on instances,
    which are the only FOLIO inventory entity with that field. holdings-storage
    rejects an unknown staffSuppress with a 422, and items silently drop it, so
    sending it there is at best a no-op and at worst breaks the cascade.

    Not-found → ``skip`` (the record is already gone). Idempotent: re-suppressing
    a record whose flags are already true is a harmless PUT, so redelivered
    deletion facts do not misbehave.
    """
    existing = _find_by_hrid(folio, search_path, hrid, list_key)
    if not existing:
        return EntityResult(action="skip")

    folio_id: str = existing["id"]
    if not dry_run:
        suppression: dict[str, Any] = {"discoverySuppress": True}
        if write_path_prefix in _STAFF_SUPPRESS_PATHS:
            suppression["staffSuppress"] = True
        folio.put(
            f"{write_path_prefix}/{folio_id}",
            {**_strip_readonly(existing), **suppression},
        )
        logger.info("suppressed", hrid=hrid, folio_id=folio_id)
    return EntityResult(action="suppress", id=folio_id)


def _delete_entity(
    folio: FolioInventoryOps,
    *,
    search_path: str,
    list_key: str,
    write_path_prefix: str,
    hrid: str,
    dry_run: bool,
) -> EntityResult:
    """Resolve an entity by hrid and hard-delete it.

    Not-found → ``skip`` (already gone); the inventory client also treats a 404
    on the DELETE as a no-op, so this is idempotent under redelivery/races. A
    non-404 error (e.g. FOLIO 400 because a child still references this record)
    raises, which aborts the parent's delete in :func:`delete_by_guid` — that is
    intentional, since deleting a parent while a child remains would orphan it.
    """
    existing = _find_by_hrid(folio, search_path, hrid, list_key)
    if not existing:
        return EntityResult(action="skip")

    folio_id: str = existing["id"]
    if not dry_run:
        folio.delete(f"{write_path_prefix}/{folio_id}")
        logger.info("deleted hrid=%s folio_id=%s", hrid, folio_id)
    return EntityResult(action="delete", id=folio_id)


def _resolve_item_note_types(payload: dict, ref_cache: RefCache) -> dict:
    """Resolve noteType names to itemNoteTypeId UUIDs in item notes."""
    if "notes" not in payload or not isinstance(payload["notes"], list):
        return payload

    resolved_notes = []
    for note in payload["notes"]:
        if not isinstance(note, dict):
            resolved_notes.append(note)
            continue

        resolved_note = dict(note)
        if "noteType" in resolved_note and "itemNoteTypeId" not in resolved_note:
            note_type_name = resolved_note.pop("noteType")
            item_note_type_id = ref_cache.resolve_item_note_type(note_type_name)
            if item_note_type_id:
                resolved_note["itemNoteTypeId"] = item_note_type_id
            else:
                logger.warning("Unresolved item note type: %s", note_type_name)

        resolved_notes.append(resolved_note)

    return {**payload, "notes": resolved_notes}


def _upsert_entity(
    folio: FolioInventoryOps,
    *,
    search_path: str,
    list_key: str,
    write_path_prefix: str,
    hrid: str,
    payload: dict,
    dry_run: bool,
) -> tuple[str, str | None]:
    """
    Resolve an entity by hrid and create or update it.

    Returns (action, folio_id).
    """
    existing = _find_by_hrid(folio, search_path, hrid, list_key)
    if existing:
        folio_id: str | None = existing["id"]
        if not dry_run:
            merged = {**_strip_readonly(existing), **payload, "id": folio_id}
            folio.put(f"{write_path_prefix}/{folio_id}", merged)
            logger.info("updated hrid=%s folio_id=%s", hrid, folio_id)
        return "update", folio_id
    else:
        if not dry_run:
            created = folio.post(write_path_prefix, payload)
            folio_id = created.get("id") if isinstance(created, dict) else None
            if not folio_id:
                # Some FOLIO inventory POSTs return 201 with an empty body — the
                # new id comes back only in the Location header, which the
                # folio_post callable drops. Re-resolve by the hrid we just wrote.
                refetched = _find_by_hrid(folio, search_path, hrid, list_key)
                folio_id = refetched["id"] if refetched else None
            if not folio_id:
                raise RuntimeError(
                    f"created {write_path_prefix} hrid={hrid} but could not resolve its id"
                )
            logger.info("created hrid=%s folio_id=%s", hrid, folio_id)
            return "create", folio_id
        return "create", None


def _best_effort_delete(
    folio: FolioInventoryOps,
    *,
    path: str,
    source_id: str,
    entity: str,
) -> None:
    """Attempt cleanup for create-path partial failures; never raise."""
    try:
        folio.delete(path)
        logger.info(
            "rollback_deleted entity=%s path=%s source_id=%s", entity, path, source_id
        )
    except Exception as exc:
        logger.warning(
            "rollback_delete_failed entity=%s path=%s source_id=%s error=%s",
            entity,
            path,
            source_id,
            exc,
        )


# ── build_payloads path ───────────────────────────────────────────────────────


def upsert_from_payloads(
    mapped: MappedPayloads,
    folio: FolioInventoryOps,
    *,
    ref_cache: RefCache | None = None,
    dry_run: bool = False,
) -> UpsertResult:
    """
    Upsert a record using the output of mapping.build_payloads().

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
        logger.error("api error source_id=%s detail=%s", source_id, exc)

    return result


# ── reconciler delete path (suppress / hard-delete) ─────────────────────────────

# The cascade order is child → parent: (attr, search_path, list_key, hrid builder).
# For hard delete this order is mandatory — FOLIO refuses to delete a parent while
# a child still references it; for suppress it mirrors the reverse of create order.
_CASCADE_ENTITIES: list[tuple[str, str, str, Callable[[str], str]]] = [
    ("item", "/inventory/items", "items", _item_hrid),
    ("holdings", "/holdings-storage/holdings", "holdingsRecords", _holdings_hrid),
    ("instance", "/inventory/instances", "instances", _instance_hrid),
]

# An entity operation: resolve by hrid and apply an action (suppress / delete).
_EntityOp = Callable[..., EntityResult]


def _cascade_by_guid(
    guid: str,
    folio: FolioInventoryOps,
    entity_op: _EntityOp,
    *,
    dry_run: bool,
) -> GuidCascadeResult:
    """Apply ``entity_op`` to a superseded GUID's records, child-first.

    The single try/except is load-bearing for hard delete: if a child op fails,
    the cascade stops before the parent, so a parent is never actioned while a
    failed child may still reference it. Records already gone are skipped. A hrid
    maps directly from the GUID (``AxC-{entity}-{guid}``), the same scheme the
    upsert path writes, so no MARC parse or mapping is needed here.
    """
    result = GuidCascadeResult(guid=guid)
    try:
        for attr, search_path, list_key, hrid_of in _CASCADE_ENTITIES:
            setattr(
                result,
                attr,
                entity_op(
                    folio,
                    search_path=search_path,
                    list_key=list_key,
                    write_path_prefix=search_path,
                    hrid=hrid_of(guid),
                    dry_run=dry_run,
                ),
            )
    except Exception as exc:
        result.errors.append(UpsertError(type="api", detail=str(exc)))
        logger.error("reconcile cascade error", guid=guid, error=str(exc), exc_info=True)

    return result


def suppress_by_guid(
    guid: str,
    folio: FolioInventoryOps,
    *,
    dry_run: bool = False,
) -> GuidCascadeResult:
    """Soft-suppress the FOLIO records for a superseded GUID, child-first.

    Reversible and auditable: sets discoverySuppress (and staffSuppress on the
    instance) rather than hard-deleting. Idempotent — a redelivered fact re-sets
    flags already true.
    """
    return _cascade_by_guid(guid, folio, _suppress_entity, dry_run=dry_run)


def delete_by_guid(
    guid: str,
    folio: FolioInventoryOps,
    *,
    dry_run: bool = False,
) -> GuidCascadeResult:
    """Hard-delete the FOLIO records for a superseded GUID, child-first.

    Irreversible. The child-first order is mandatory (FOLIO enforces referential
    integrity), and the cascade aborts if a child delete fails so a parent is
    never deleted while a child remains. The inventory client treats a 404 on the
    DELETE as a no-op, so redelivered facts and races are handled cleanly.
    """
    return _cascade_by_guid(guid, folio, _delete_entity, dry_run=dry_run)
