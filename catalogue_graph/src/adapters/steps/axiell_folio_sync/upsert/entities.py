"""
Single-entity FOLIO operations: resolve-by-hrid, create/update, suppress, delete.

These are the low-level building blocks shared by the two write paths — the
create/update orchestration in ``writer.py`` and the guid-cascade reconciler in
``reconcile.py``. Each operates on exactly one FOLIO entity (instance / holdings /
item) and returns an :class:`EntityResult`; none of them decide cascade order or
roll back siblings — that is the callers' job.
"""

from __future__ import annotations

from typing import Any

import structlog

from ..folio import FolioInventoryOps, RefCache
from ..results import EntityResult

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

# Write-path prefixes whose entity carries a staffSuppress field. Only FOLIO
# instances do; holdings-storage 422s on it and items silently drop it.
_STAFF_SUPPRESS_PATHS: frozenset[str] = frozenset({"/inventory/instances"})


def _strip_readonly(record: dict) -> dict:
    """Remove computed read-only fields that FOLIO rejects on PUT."""
    return {k: v for k, v in record.items() if k not in _READONLY_FIELDS}


def _find_by_hrid(
    folio: FolioInventoryOps, path: str, hrid: str, list_key: str
) -> dict | None:
    """Return the first FOLIO record matching hrid via CQL, or None.

    ``None`` means an *empty result* — no record has this hrid. A lookup *failure*
    (network/FOLIO error) is a different thing and always propagates: it must never
    be collapsed into "not found", because both callers key an irreversible
    decision on absence. The delete cascade would report a still-live record as a
    cleanly-actioned skip (and a deletion fact only arrives once); the upsert path
    would take the create branch and POST a record that may already exist. Both
    callers already wrap this in a try/except that records the error and either
    aborts the cascade or rolls back, so a raised lookup slots straight in.
    """
    result = folio.get(path, {"query": f'hrid=="{hrid}"', "limit": 1})
    records = result.get(list_key, [])
    return records[0] if records else None


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
    deletion facts do not misbehave. A failed hrid lookup is not "already gone" —
    it propagates (see :func:`_find_by_hrid`) so the cascade records it and aborts.
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
    intentional, since deleting a parent while a child remains would orphan it. A
    failed hrid lookup raises for the same reason (see :func:`_find_by_hrid`), so a
    swallowed outage cannot let the cascade proceed to the holdings/instance delete
    while the item may still exist.
    """
    existing = _find_by_hrid(folio, search_path, hrid, list_key)
    if not existing:
        return EntityResult(action="skip")

    folio_id: str = existing["id"]
    if not dry_run:
        folio.delete(f"{write_path_prefix}/{folio_id}")
        logger.info("deleted", hrid=hrid, folio_id=folio_id)
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
