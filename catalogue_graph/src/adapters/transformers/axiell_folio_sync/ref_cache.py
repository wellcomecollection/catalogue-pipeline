"""
FOLIO reference data cache.

Fetches location codes, material types, loan types, and instance type UUIDs
once at startup from a live FOLIO tenant and caches them for the lifetime of
the process. All lookups are case-insensitive.

Usage
─────
    cache = RefCache(folio_get).load()
    location_uuid = cache.resolve_location("STACK")
    material_type_uuid = cache.resolve_material_type("book")
    loan_type_uuid = cache.resolve_loan_type("Can circulate")
    holdings_source_uuid = cache.resolve_holdings_source("MARC")
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from typing import Any, cast

logger = logging.getLogger(__name__)

# Default instance type to resolve when no code is available in the MARC record.
DEFAULT_INSTANCE_TYPE_NAME = "text"


class RefCache:
    """In-memory cache of FOLIO tenant reference data UUIDs."""

    def __init__(self, folio_get: Callable):
        """
        Args:
            folio_get: The ``folio_get(path, params)`` callable already wired
                       to the authenticated FOLIO tenant (from the notebook or
                       FolioClient.request).
        """
        self._get = folio_get
        self._locations: dict[str, str] = {}  # code.lower() → UUID
        self._location_names: dict[str, str] = {}  # name.lower() → UUID
        self._material_types: dict[str, str] = {}  # name.lower() → UUID
        self._loan_types: dict[str, str] = {}  # name.lower() → UUID
        self._holdings_sources: dict[str, str] = {}  # name.lower() → UUID
        self._item_note_types: dict[str, str] = {}  # name.lower() → UUID
        self._location_records: list[dict[str, Any]] = []
        self._material_type_records: list[dict[str, Any]] = []
        self._loan_type_records: list[dict[str, Any]] = []
        self._holdings_source_records: list[dict[str, Any]] = []
        self._item_note_type_records: list[dict[str, Any]] = []
        self._instance_type_records: list[dict[str, Any]] = []
        self._instance_type_id: str | None = None
        self._loaded = False

    # ── loading ───────────────────────────────────────────────────────────────

    def load(self) -> RefCache:
        """Fetch all reference data from FOLIO. Returns self for chaining."""
        self._location_records = self._fetch_records("/locations", "locations")
        self._locations = self._build_map(self._location_records, key="code")
        self._location_names = self._build_map(self._location_records, key="name")
        logger.info("RefCache: %d locations", len(self._locations))

        self._material_type_records = self._fetch_records("/material-types", "mtypes")
        self._material_types = self._build_map(self._material_type_records, key="name")
        logger.info("RefCache: %d material types", len(self._material_types))

        self._loan_type_records = self._fetch_records("/loan-types", "loantypes")
        self._loan_types = self._build_map(self._loan_type_records, key="name")
        logger.info("RefCache: %d loan types", len(self._loan_types))

        # Folio tenants may expose different response keys here; handle both.
        data = self._get("/holdings-sources", {"limit": 2000})
        rows = data.get("holdingsRecordsSources") or data.get("holdingsSources") or []
        self._holdings_source_records = [r for r in rows if isinstance(r, dict)]
        self._holdings_sources = self._build_map(
            self._holdings_source_records, key="name"
        )
        logger.info("RefCache: %d holdings sources", len(self._holdings_sources))

        self._item_note_type_records = self._fetch_records(
            "/item-note-types", "itemNoteTypes"
        )
        self._item_note_types = self._build_map(
            self._item_note_type_records, key="name"
        )
        logger.info("RefCache: %d item note types", len(self._item_note_types))

        self._instance_type_id = self._fetch_instance_type_id()
        logger.info("RefCache: instance type id = %s", self._instance_type_id)

        self._loaded = True
        return self

    def _fetch_records(self, path: str, list_key: str) -> list[dict[str, Any]]:
        data = self._get(path, {"limit": 2000})
        rows = data.get(list_key, [])
        if not isinstance(rows, list):
            return []
        return [r for r in rows if isinstance(r, dict)]

    def _build_map(self, rows: list[dict[str, Any]], *, key: str) -> dict[str, str]:
        return {r[key].lower(): r["id"] for r in rows if r.get(key) and r.get("id")}

    def _project_fields(
        self, rows: list[dict[str, Any]], fields: tuple[str, ...]
    ) -> list[dict[str, Any]]:
        """Return only selected keys from each API row (missing keys become None)."""
        return [{field: row.get(field) for field in fields} for row in rows]

    def _format_table(
        self, rows: list[dict[str, Any]], columns: tuple[str, ...]
    ) -> str:
        """Render rows as a simple fixed-width ASCII table."""
        if not rows:
            header = " | ".join(columns)
            divider = "-+-".join("-" * len(col) for col in columns)
            return "\n".join([header, divider, "(no rows)"])

        table_rows = [
            ["" if row.get(col) is None else str(row.get(col)) for col in columns]
            for row in rows
        ]
        widths = [len(col) for col in columns]
        for values in table_rows:
            for i, value in enumerate(values):
                widths[i] = max(widths[i], len(value))

        def _render(values: list[str]) -> str:
            return " | ".join(values[i].ljust(widths[i]) for i in range(len(values)))

        header = _render(list(columns))
        divider = "-+-".join("-" * w for w in widths)
        body = [_render(values) for values in table_rows]
        return "\n".join([header, divider, *body])

    def _fetch_instance_type_id(self) -> str | None:
        data = self._get("/instance-types", {"limit": 500})
        types = data.get("instanceTypes", [])
        self._instance_type_records = [r for r in types if isinstance(r, dict)]
        for r in self._instance_type_records:
            if r.get("name", "").lower() == DEFAULT_INSTANCE_TYPE_NAME:
                return cast("str", r["id"])
        # Fall back to first available type
        if self._instance_type_records:
            return cast("str", self._instance_type_records[0]["id"])
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

    def instance_type_id(self) -> str:
        if not self._instance_type_id:
            raise RuntimeError("RefCache not loaded — call .load() first")
        return self._instance_type_id

    # ── introspection ─────────────────────────────────────────────────────────

    def summary(self) -> dict:
        return {
            "locations": len(self._locations),
            "location_codes": sorted(self._locations.keys()),
            "location_records": self._project_fields(
                self._location_records,
                ("id", "name", "code"),
            ),
            "material_types": len(self._material_types),
            "material_type_names": sorted(self._material_types.keys()),
            "material_type_records": self._project_fields(
                self._material_type_records,
                ("id", "name"),
            ),
            "loan_types": len(self._loan_types),
            "loan_type_names": sorted(self._loan_types.keys()),
            "loan_type_records": self._project_fields(
                self._loan_type_records,
                ("id", "name", "code"),
            ),
            "holdings_sources": len(self._holdings_sources),
            "holdings_source_names": sorted(self._holdings_sources.keys()),
            "holdings_source_records": self._project_fields(
                self._holdings_source_records,
                ("id", "name"),
            ),
            "item_note_types": len(self._item_note_types),
            "item_note_type_names": sorted(self._item_note_types.keys()),
            "item_note_type_records": self._project_fields(
                self._item_note_type_records,
                ("id", "name"),
            ),
            "instance_type_records": self._instance_type_records,
            "instance_type_id": self._instance_type_id,
        }

    def summary_tables(self) -> dict[str, str]:
        """Return print-friendly tables for location, material type, and loan type records."""
        location_rows = self._project_fields(
            self._location_records,
            ("id", "name", "code"),
        )
        material_rows = self._project_fields(
            self._material_type_records,
            ("id", "name"),
        )
        loan_rows = self._project_fields(
            self._loan_type_records,
            ("id", "name", "code"),
        )
        note_type_rows = self._project_fields(
            self._item_note_type_records,
            ("id", "name"),
        )
        return {
            "locations_table": self._format_table(
                location_rows, ("id", "name", "code")
            ),
            "material_types_table": self._format_table(material_rows, ("id", "name")),
            "loan_types_table": self._format_table(loan_rows, ("id", "name", "code")),
            "item_note_types_table": self._format_table(note_type_rows, ("id", "name")),
        }

    def print_summary_tables(self) -> None:
        """Print tabular location, material type, and loan type records to stdout."""
        tables = self.summary_tables()
        print("Locations")
        print(tables["locations_table"])
        print()
        print("Material Types")
        print(tables["material_types_table"])
        print()
        print("Loan Types")
        print(tables["loan_types_table"])
        print()
        print("Item Note Types")
        print(tables["item_note_types_table"])

    def unresolved_report(self, records: list) -> dict:
        """Given a list of CanonicalRecord, report which codes are not in the cache."""
        missing_locations: set[str] = set()
        missing_material_types: set[str] = set()
        missing_loan_types: set[str] = set()
        for r in records:
            if r.location_code and not self.resolve_location(r.location_code):
                missing_locations.add(r.location_code)
            if r.material_type_code and not self.resolve_material_type(
                r.material_type_code
            ):
                missing_material_types.add(r.material_type_code)
            if r.loan_type_code and not self.resolve_loan_type(r.loan_type_code):
                missing_loan_types.add(r.loan_type_code)
        return {
            "missing_locations": sorted(missing_locations),
            "missing_material_types": sorted(missing_material_types),
            "missing_loan_types": sorted(missing_loan_types),
        }
