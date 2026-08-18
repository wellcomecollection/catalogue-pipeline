"""
FOLIO reference data cache.

Fetches location codes, material types, loan types, holdings sources, item note
types, identifier types, and the instance type UUID once at startup from a live
FOLIO tenant and caches them for the lifetime of the process. All lookups are
case-insensitive.

Usage
─────
    cache = RefCache(folio_get).load()
    location_uuid = cache.resolve_location("STACK")
    material_type_uuid = cache.resolve_material_type("book")
    loan_type_uuid = cache.resolve_loan_type("Can circulate")
    holdings_source_uuid = cache.resolve_holdings_source("MARC")
    item_note_type_uuid = cache.resolve_item_note_type("Axiell location")
    identifier_type_uuid = cache.resolve_identifier_type("Local identifier")
"""

from __future__ import annotations

from typing import Any, cast

import structlog

from .callables import FolioInventoryOps

logger = structlog.get_logger(__name__)

# Default instance type to resolve when no code is available in the MARC record.
DEFAULT_INSTANCE_TYPE_NAME = "text"


class RefCache:
    """In-memory cache of FOLIO tenant reference data UUIDs."""

    def __init__(self, folio: FolioInventoryOps):
        """
        Args:
            folio: Inventory operations already wired to the authenticated
                   FOLIO tenant.
        """
        self._folio = folio
        self._locations: dict[str, str] = {}  # code.lower() → UUID
        self._location_names: dict[str, str] = {}  # name.lower() → UUID
        self._material_types: dict[str, str] = {}  # name.lower() → UUID
        self._loan_types: dict[str, str] = {}  # name.lower() → UUID
        self._holdings_sources: dict[str, str] = {}  # name.lower() → UUID
        self._item_note_types: dict[str, str] = {}  # name.lower() → UUID
        self._identifier_types: dict[str, str] = {}  # name.lower() → UUID
        self._instance_type_id: str | None = None
        self._loaded = False

    # ── loading ───────────────────────────────────────────────────────────────

    def load(self) -> RefCache:
        """Fetch all reference data from FOLIO. Returns self for chaining."""
        location_records = self._fetch_records("/locations", "locations")
        self._locations = self._build_map(location_records, key="code")
        self._location_names = self._build_map(location_records, key="name")
        logger.info("RefCache: %d locations", len(self._locations))

        material_type_records = self._fetch_records("/material-types", "mtypes")
        self._material_types = self._build_map(material_type_records, key="name")
        logger.info("RefCache: %d material types", len(self._material_types))

        loan_type_records = self._fetch_records("/loan-types", "loantypes")
        self._loan_types = self._build_map(loan_type_records, key="name")
        logger.info("RefCache: %d loan types", len(self._loan_types))

        # Folio tenants may expose different response keys here; handle both.
        data = self._folio.get("/holdings-sources", {"limit": 2000})
        rows = data.get("holdingsRecordsSources") or data.get("holdingsSources") or []
        holdings_source_records = [r for r in rows if isinstance(r, dict)]
        self._holdings_sources = self._build_map(holdings_source_records, key="name")
        logger.info("RefCache: %d holdings sources", len(self._holdings_sources))

        item_note_type_records = self._fetch_records(
            "/item-note-types", "itemNoteTypes"
        )
        self._item_note_types = self._build_map(item_note_type_records, key="name")
        logger.info("RefCache: %d item note types", len(self._item_note_types))

        identifier_type_records = self._fetch_records(
            "/identifier-types", "identifierTypes"
        )
        self._identifier_types = self._build_map(identifier_type_records, key="name")
        logger.info("RefCache: %d identifier types", len(self._identifier_types))

        self._instance_type_id = self._fetch_instance_type_id()
        logger.info("RefCache: instance type id = %s", self._instance_type_id)

        self._loaded = True
        return self

    def _fetch_records(self, path: str, list_key: str) -> list[dict[str, Any]]:
        data = self._folio.get(path, {"limit": 2000})
        rows = data.get(list_key, [])
        if not isinstance(rows, list):
            return []
        return [r for r in rows if isinstance(r, dict)]

    def _build_map(self, rows: list[dict[str, Any]], *, key: str) -> dict[str, str]:
        return {r[key].lower(): r["id"] for r in rows if r.get(key) and r.get("id")}

    def _fetch_instance_type_id(self) -> str | None:
        data = self._folio.get("/instance-types", {"limit": 500})
        types = data.get("instanceTypes", [])
        records = [r for r in types if isinstance(r, dict)]
        for r in records:
            if r.get("name", "").lower() == DEFAULT_INSTANCE_TYPE_NAME:
                return cast("str", r["id"])
        # Fall back to first available type
        if records:
            return cast("str", records[0]["id"])
        return None

    # ── lookups ───────────────────────────────────────────────────────────────

    def resolve_location(self, code: str | None) -> str | None:
        """Return the FOLIO UUID for a location code or name, or None."""
        key = (code or "").lower()
        return self._locations.get(key) or self._location_names.get(key)

    def resolve_material_type(self, name: str | None) -> str | None:
        """Return the FOLIO UUID for a material type name, or None."""
        return self._material_types.get((name or "").lower())

    def resolve_loan_type(self, name: str | None) -> str | None:
        """Return the FOLIO UUID for a loan type name, or None."""
        return self._loan_types.get((name or "").lower())

    def resolve_holdings_source(self, name: str | None) -> str | None:
        """Return the FOLIO UUID for a holdings source name, or None."""
        return self._holdings_sources.get((name or "").lower())

    def resolve_item_note_type(self, name: str | None) -> str | None:
        """Return the FOLIO UUID for an item note type name, or None."""
        return self._item_note_types.get((name or "").lower())

    def resolve_identifier_type(self, name: str | None) -> str | None:
        """Return the FOLIO UUID for an instance identifier type name, or None."""
        return self._identifier_types.get((name or "").lower())

    def instance_type_id(self) -> str:
        if not self._instance_type_id:
            raise RuntimeError("RefCache not loaded — call .load() first")
        return self._instance_type_id
