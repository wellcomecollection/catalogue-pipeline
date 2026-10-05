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
from ..mapping import AXIELL_LOCATION_NOTE_PREFIX
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

# Fields required on create whose values FOLIO or another module owns after
# creation. Updates preserve FOLIO's current value; see :func:`_payload_for_update`.
_CREATE_ONLY_FIELDS: frozenset[str] = frozenset({"status"})


def _strip_readonly(record: dict) -> dict:
    """Remove computed read-only fields that FOLIO rejects on PUT."""
    return {k: v for k, v in record.items() if k not in _READONLY_FIELDS}


def _payload_for_update(existing: dict, payload: dict) -> dict:
    """The payload as it should be applied to a record FOLIO already holds.

    Create-only fields take the value read back from FOLIO, so an update cannot
    overwrite state another module owns. Ours stays only as a backstop for a
    fetched record that carries none, because these fields are required on update
    too and dropping the key outright would make the PUT invalid.

    In practice the hrid lookup returns the whole record, since
    ``GET /inventory/items`` answers with full ``item.json`` objects, so the
    backstop should never be the one that applies.
    """
    updated = dict(payload)
    for field in _CREATE_ONLY_FIELDS & payload.keys():
        if field in existing:
            updated[field] = existing[field]
    return updated


def _is_axiell_location_note(note: object) -> bool:
    """True for the one administrative note this sync owns.

    Matched on the label alone — ``administrativeNotes`` is a bare list of strings
    with no id or type, so reading the text back is the only identity available.
    Case-insensitive, so a note whose capitalisation was "corrected" in the FOLIO
    UI is still reclaimed rather than duplicated alongside a new one.

    A note whose label has been reworded is indistinguishable from a cataloguer's
    own and is deliberately left alone; :func:`_stray_location_notes` reports it.
    """
    if not isinstance(note, str):
        return False
    return note.strip().lower().startswith(f"{AXIELL_LOCATION_NOTE_PREFIX.lower()}:")


def _merge_admin_notes(existing: list, incoming: list) -> list:
    """Replace only the Axiell location note, keeping every other note.

    ``administrativeNotes`` is a flat list of strings with no ids, so the shallow
    payload-wins merge in :func:`_upsert_entity` would swap the whole list for the
    single note the mapping builds, silently dropping anything a cataloguer added
    in FOLIO. Ours is identified by :func:`_is_axiell_location_note`; the rest are
    preserved in their original order, ours appended last.
    """
    kept = [note for note in existing if not _is_axiell_location_note(note)]
    return kept + list(incoming)


# Substring that makes a note *look* like a location note the sync wrote.
# Deliberately broad: a false positive costs a log line, a false negative lets a
# duplicate accumulate unseen, which is the thing being guarded against.
_STRAY_NOTE_HINT = "axiell"


def _stray_location_notes(existing: list) -> list[str]:
    """Notes that mention Axiell but do not carry our label.

    Almost always a location note whose label was edited away in the FOLIO UI:
    the sync can no longer claim it, so it writes a fresh note and the old one
    stays behind as a duplicate. Nothing can be done about that automatically — at
    that point the note is indistinguishable from one a cataloguer wrote — but it
    can be surfaced rather than accumulating silently, which is all this does.
    """
    return [
        note
        for note in existing
        if isinstance(note, str)
        and _STRAY_NOTE_HINT in note.lower()
        and not _is_axiell_location_note(note)
    ]


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
    stray_notes: list[str] | None = None,
) -> tuple[str, str | None]:
    """
    Resolve an entity by hrid and create or update it.

    ``stray_notes`` is an optional sink: any existing administrative note that
    looks like one of ours but carries neither marker is appended to it, for the
    caller to record. Populated on dry runs too, so a dry run surfaces the same
    warning as a real one.

    Returns (action, folio_id).
    """
    existing = _find_by_hrid(folio, search_path, hrid, list_key)
    if existing:
        folio_id: str | None = existing["id"]
        existing_notes = existing.get("administrativeNotes") or []
        # Only meaningful for the entity the sync actually writes notes to.
        if "administrativeNotes" in payload:
            strays = _stray_location_notes(existing_notes)
            if strays:
                logger.warning(
                    "stray_location_notes",
                    hrid=hrid,
                    folio_id=folio_id,
                    notes=strays,
                )
                if stray_notes is not None:
                    stray_notes.extend(strays)
        if not dry_run:
            merged = {
                **_strip_readonly(existing),
                **_payload_for_update(existing, payload),
                "id": folio_id,
            }
            if "administrativeNotes" in payload:
                merged["administrativeNotes"] = _merge_admin_notes(
                    existing_notes, payload["administrativeNotes"]
                )
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
