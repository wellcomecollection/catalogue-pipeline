"""Models for the FOLIO item-enrichment payload.

The FOLIO OAI-PMH feed (``marc21_withholdings``) carries item data in MARC 952
datafields but no item UUID. To give the public catalogue a stable item id we enrich
each harvested instance with item UUIDs fetched from mod-inventory-storage's
``POST /oai-pmh-view/enrichedInstances`` endpoint.

Response contract (per the mod-inventory-storage API docs, oai-pmh-view): each record
is ``{"instanceId": "<uuid>", "itemsAndHoldingsFields": {"items": [...]}}``. Each item
carries ``id`` (the item UUID), ``volume``, ``enumeration``, ``materialType`` (string),
and the **nested objects** ``callNumber`` (``{prefix, suffix, typeId, callNumber}``) and
``location`` (``{location: {...names...}}``). ``from_api`` flattens those and stays
tolerant of extra/missing fields so upstream variation does not break harvesting.

See https://github.com/wellcomecollection/catalogue-pipeline/pull/3438 for the design.
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


def _first_present(data: dict[str, Any], *keys: str) -> Any | None:
    """Return the first non-null value among ``keys`` (case-insensitive).

    Case-insensitive because the live ``enrichedInstances`` stream lowercases its
    wrapper keys (``instanceid``, ``itemsandholdingsfields``) even though item fields
    are camelCase, so callers pass the documented camelCase key and this matches both.
    """
    lowered = {k.lower(): v for k, v in data.items()}
    for key in keys:
        value = lowered.get(key.lower())
        if value is not None:
            return value
    return None


def _opt_str(value: Any | None) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _call_number(value: Any | None) -> str | None:
    """Flatten the nested ``callNumber`` object to its call-number string.

    The API returns ``callNumber`` as ``{prefix, suffix, typeId, callNumber}``; older
    or simplified shapes may send a plain string. Handle both.
    """
    if isinstance(value, dict):
        return _opt_str(_first_present(value, "callNumber"))
    return _opt_str(value)


def _location_name(value: Any | None) -> str | None:
    """Extract a readable location label from the nested ``location`` object.

    Shape is ``{"location": {libraryName, institutionName, ...}, "name": ...}``; fall
    back through the available name fields, or accept a plain string.
    """
    if isinstance(value, dict):
        name = _first_present(value, "name")
        if name is None:
            inner = value.get("location")
            if isinstance(inner, dict):
                name = _first_present(
                    inner, "libraryName", "campusName", "institutionName"
                )
        return _opt_str(name)
    return _opt_str(value)


class FolioEnrichedItem(BaseModel):
    """A single FOLIO item, keyed by its inventory UUID.

    ``id`` is the load-bearing field: it is the stable identifier the OAI-PMH 952
    datafield cannot provide.
    """

    id: str
    barcode: str | None = None
    volume: str | None = None
    enumeration: str | None = None
    material_type: str | None = None
    call_number: str | None = None
    location: str | None = None

    @classmethod
    def from_api(cls, data: dict[str, Any]) -> FolioEnrichedItem:
        item_id = _first_present(data, "id")
        if item_id is None:
            # The id is the stable identifier the whole enrichment exists to provide;
            # an item without one cannot mint a valid identifier. Reject it explicitly
            # rather than emit an item identified by the literal string "None".
            raise ValueError("FOLIO enriched item is missing an id")
        return cls(
            id=str(item_id),
            barcode=_opt_str(_first_present(data, "barcode")),
            volume=_opt_str(_first_present(data, "volume")),
            enumeration=_opt_str(_first_present(data, "enumeration")),
            material_type=_opt_str(_first_present(data, "materialType")),
            call_number=_call_number(_first_present(data, "callNumber")),
            location=_location_name(_first_present(data, "location")),
        )


class FolioEnrichedInstance(BaseModel):
    """Items for a single FOLIO instance.

    Keyed by ``instance_id`` so it joins directly onto a harvested bib row at transform
    time. (The enricher re-keys this to the bib store's OAI identifier before storing —
    see ``enricher.py`` — because the API is queried by bare instance UUID while the bib
    store row id is the full OAI identifier.)
    """

    instance_id: str
    items: list[FolioEnrichedItem] = Field(default_factory=list)

    @classmethod
    def from_api(cls, data: dict[str, Any]) -> FolioEnrichedInstance:
        """Parse one record from the ``enrichedInstances`` response."""
        nested = _first_present(data, "itemsAndHoldingsFields") or {}
        raw_items = (
            _first_present(data, "items")
            or (nested.get("items") if isinstance(nested, dict) else None)
            or []
        )
        instance_id = _first_present(data, "instanceId")
        if instance_id is None and isinstance(nested, dict):
            instance_id = _first_present(nested, "instanceId")
        if instance_id is None:
            # Without an instance id the row cannot be keyed to a bib record. Fail
            # loudly rather than store an unjoinable row keyed by the string "None".
            raise ValueError("FOLIO enriched instance is missing instanceId")

        # A single unparseable item (e.g. one missing its id) raises and fails the whole
        # batch — an item we cannot identify is a contract violation, not a skip.
        return cls(
            instance_id=str(instance_id),
            items=[FolioEnrichedItem.from_api(i) for i in raw_items],
        )

    def to_store_content(self) -> str:
        """Serialise to the JSON stored in the ``folio_items_table`` content column."""
        return self.model_dump_json()

    @classmethod
    def from_store_content(cls, content: str) -> FolioEnrichedInstance:
        """Parse a row's stored ``content`` back into an instance."""
        return cls.model_validate_json(content)
