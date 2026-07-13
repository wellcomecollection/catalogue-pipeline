"""
FOLIO Inventory upsert orchestrator.

Entry point:
  upsert_from_payloads() — takes the dict produced by mapping.build_payloads()

Enforces write order:  Instance → Holdings → Item.
Deleted records are soft-suppressed (discoverySuppress + staffSuppress).
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

import logging
from collections.abc import Callable

from .ref_cache import RefCache

logger = logging.getLogger(__name__)

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


# ── shared helpers ────────────────────────────────────────────────────────────


def _find_by_hrid(
    folio_get: Callable, path: str, hrid: str, list_key: str
) -> dict | None:
    """Return the first FOLIO record matching hrid via CQL, or None."""
    try:
        result = folio_get(path, {"query": f'hrid=="{hrid}"', "limit": 1})
        records = result.get(list_key, [])
        return records[0] if records else None
    except Exception as exc:
        logger.warning("hrid lookup failed path=%s hrid=%s error=%s", path, hrid, exc)
        return None


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
    folio_get: Callable,
    folio_post: Callable,
    folio_put: Callable,
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
    existing = _find_by_hrid(folio_get, search_path, hrid, list_key)
    if existing:
        folio_id: str | None = existing["id"]
        if not dry_run:
            merged = {**_strip_readonly(existing), **payload, "id": folio_id}
            folio_put(f"{write_path_prefix}/{folio_id}", merged)
            logger.info("updated hrid=%s folio_id=%s", hrid, folio_id)
        return "update", folio_id
    else:
        if not dry_run:
            created = folio_post(write_path_prefix, payload)
            folio_id = created.get("id") if isinstance(created, dict) else None
            if not folio_id:
                # Some FOLIO inventory POSTs return 201 with an empty body — the
                # new id comes back only in the Location header, which the
                # folio_post callable drops. Re-resolve by the hrid we just wrote.
                refetched = _find_by_hrid(folio_get, search_path, hrid, list_key)
                folio_id = refetched["id"] if refetched else None
            if not folio_id:
                raise RuntimeError(
                    f"created {write_path_prefix} hrid={hrid} but could not resolve its id"
                )
            logger.info("created hrid=%s folio_id=%s", hrid, folio_id)
            return "create", folio_id
        return "create", None


def _best_effort_delete(
    folio_delete: Callable,
    *,
    path: str,
    source_id: str,
    entity: str,
) -> None:
    """Attempt cleanup for create-path partial failures; never raise."""
    try:
        folio_delete(path)
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
    mapped: dict,
    folio_get: Callable,
    folio_post: Callable,
    folio_put: Callable,
    folio_delete: Callable,
    *,
    ref_cache: RefCache | None = None,
    dry_run: bool = False,
) -> dict:
    """
    Upsert a record using the output of mapping.build_payloads().

    Args:
        mapped:   Dict with keys "instance", "holdings", "item", "meta".
        folio_get/post/put/delete: Authenticated OKAPI callables.
        ref_cache: RefCache instance for resolving note types (optional).
        dry_run:  If True, plan without writing.
    """
    meta = mapped.get("meta", {})
    source_id: str = meta.get("source_id", "unknown")
    deleted: bool = bool(meta.get("deleted", False))
    instance_hrid: str = meta.get("instance_hrid", f"axiell:{source_id}")
    holdings_hrid: str = meta.get("holdings_hrid", f"{instance_hrid}-holding-unknown")
    item_hrid: str = meta.get("item_hrid", f"{instance_hrid}-item-unknown")

    result: dict = {
        "source_id": source_id,
        "mapping_version": meta.get("mapping_version"),
        "instance": {"action": None, "id": None},
        "holdings": {"action": None, "id": None},
        "item": {"action": None, "id": None},
        "errors": [],
    }

    created_instance_id: str | None = None
    created_holdings_id: str | None = None

    try:
        # ── Instance ────────────────────────────────────────────────────────
        action, instance_id = _upsert_entity(
            folio_get,
            folio_post,
            folio_put,
            search_path="/inventory/instances",
            list_key="instances",
            write_path_prefix="/inventory/instances",
            hrid=instance_hrid,
            payload=mapped["instance"],
            dry_run=dry_run,
        )
        result["instance"] = {"action": action, "id": instance_id}
        if action == "create" and not dry_run:
            created_instance_id = instance_id
        if dry_run:
            instance_id = f"dry-run:{instance_hrid}"

        # ── Holdings ────────────────────────────────────────────────────────
        holdings_payload = {**mapped["holdings"], "instanceId": instance_id}
        action, holdings_id = _upsert_entity(
            folio_get,
            folio_post,
            folio_put,
            search_path="/holdings-storage/holdings",
            list_key="holdingsRecords",
            write_path_prefix="/holdings-storage/holdings",
            hrid=holdings_hrid,
            payload=holdings_payload,
            dry_run=dry_run,
        )
        result["holdings"] = {"action": action, "id": holdings_id}
        if action == "create" and not dry_run:
            created_holdings_id = holdings_id
        if dry_run:
            holdings_id = f"dry-run:{holdings_hrid}"

        # ── Item ────────────────────────────────────────────────────────────
        if deleted:
            existing_item = _find_by_hrid(
                folio_get, "/inventory/items", item_hrid, "items"
            )
            if existing_item:
                result["item"] = {"action": "suppress", "id": existing_item["id"]}
                if not dry_run:
                    folio_put(
                        f"/inventory/items/{existing_item['id']}",
                        {
                            **_strip_readonly(existing_item),
                            "discoverySuppress": True,
                            "staffSuppress": True,
                        },
                    )
                    logger.info("suppressed item source_id=%s", source_id)
            else:
                result["item"] = {"action": "skip", "id": None}
        else:
            item_payload = {**mapped["item"], "holdingsRecordId": holdings_id}
            # Resolve item note types if ref_cache is available
            if ref_cache:
                item_payload = _resolve_item_note_types(item_payload, ref_cache)
            action, item_id = _upsert_entity(
                folio_get,
                folio_post,
                folio_put,
                search_path="/inventory/items",
                list_key="items",
                write_path_prefix="/inventory/items",
                hrid=item_hrid,
                payload=item_payload,
                dry_run=dry_run,
            )
            result["item"] = {"action": action, "id": item_id}

    except Exception as exc:
        if not dry_run:
            if created_holdings_id:
                _best_effort_delete(
                    folio_delete,
                    path=f"/holdings-storage/holdings/{created_holdings_id}",
                    source_id=source_id,
                    entity="holdings",
                )
            if created_instance_id:
                _best_effort_delete(
                    folio_delete,
                    path=f"/inventory/instances/{created_instance_id}",
                    source_id=source_id,
                    entity="instance",
                )

        result["errors"].append({"type": "api", "detail": str(exc)})
        logger.error("api error source_id=%s detail=%s", source_id, exc)

    return result
