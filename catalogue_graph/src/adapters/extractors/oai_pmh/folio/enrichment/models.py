"""Models for the FOLIO item-enrichment payload.

The FOLIO OAI-PMH feed (``marc21_withholdings``) carries item/holdings data in
MARC 952 datafields but no item UUID. To give the public catalogue a stable item
id we enrich each harvested instance with item and holdings UUIDs fetched from
mod-inventory-storage's ``oai-pmh-view/enrichedInstances`` endpoint.

These models describe:
  * what we read back from ``enrichedInstances`` (``FolioEnrichedInstance``), and
  * what we persist, per instance, into the ``folio_items_table`` store
    (the same model, serialised to JSON in the store ``content`` column).

The precise ``enrichedInstances`` response contract is still an open question, so
``from_api`` is intentionally tolerant: it reads the fields we need and ignores
everything else, so a change in the upstream shape does not break harvesting.

See https://github.com/wellcomecollection/catalogue-pipeline/pull/3438 for the design.
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


def _first_present(data: dict[str, Any], *keys: str) -> Any | None:
    """Return the first non-null value among ``keys`` (case-insensitive)."""
    lowered = {k.lower(): v for k, v in data.items()}
    for key in keys:
        value = lowered.get(key.lower())
        if value is not None:
            return value
    return None


class FolioEnrichedItem(BaseModel):
    """A single FOLIO item, keyed by its inventory UUID.

    ``id`` is the load-bearing field: it is the stable identifier the OAI-PMH 952
    datafield cannot provide.
    """

    id: str
    holdings_record_id: str | None = None
    barcode: str | None = None
    call_number: str | None = None
    location: str | None = None
    material_type: str | None = None
    copy_number: str | None = None
    suppress_from_discovery: bool = False

    @classmethod
    def from_api(cls, data: dict[str, Any]) -> FolioEnrichedItem:
        return cls(
            id=str(_first_present(data, "id", "itemId")),
            holdings_record_id=_opt_str(_first_present(data, "holdingsRecordId")),
            barcode=_opt_str(_first_present(data, "barcode")),
            call_number=_opt_str(
                _first_present(data, "callNumber", "itemLevelCallNumber")
            ),
            location=_opt_str(
                _first_present(
                    data, "location", "permanentLocation", "effectiveLocation"
                )
            ),
            material_type=_opt_str(
                _first_present(data, "materialType", "materialTypeId")
            ),
            copy_number=_opt_str(_first_present(data, "copyNumber")),
            suppress_from_discovery=bool(
                _first_present(data, "suppressFromDiscovery", "discoverySuppress")
                or False
            ),
        )


class FolioEnrichedHolding(BaseModel):
    """A FOLIO holdings record, keyed by its inventory UUID."""

    id: str
    call_number: str | None = None
    location: str | None = None

    @classmethod
    def from_api(cls, data: dict[str, Any]) -> FolioEnrichedHolding:
        return cls(
            id=str(_first_present(data, "id", "holdingsId")),
            call_number=_opt_str(_first_present(data, "callNumber")),
            location=_opt_str(_first_present(data, "location", "permanentLocation")),
        )


class FolioEnrichedInstance(BaseModel):
    """Items and holdings for a single FOLIO instance.

    Keyed by ``instance_id`` (the bib store's ``id``), so it joins directly onto a
    harvested bib row at transform time.
    """

    instance_id: str
    items: list[FolioEnrichedItem] = Field(default_factory=list)
    holdings: list[FolioEnrichedHolding] = Field(default_factory=list)

    @classmethod
    def from_api(cls, data: dict[str, Any]) -> FolioEnrichedInstance:
        """Parse a single record from the ``enrichedInstances`` response.

        Items and holdings may be nested under ``itemsandholdingsfields`` (the
        mod-inventory-storage shape) or supplied at the top level.
        """
        nested = (
            _first_present(data, "itemsandholdingsfields", "itemsAndHoldingsFields")
            or {}
        )
        raw_items = _first_present(data, "items") or nested.get("items") or []
        raw_holdings = _first_present(data, "holdings") or nested.get("holdings") or []

        return cls(
            instance_id=str(_first_present(data, "instanceId", "instance_id", "id")),
            items=[FolioEnrichedItem.from_api(i) for i in raw_items],
            holdings=[FolioEnrichedHolding.from_api(h) for h in raw_holdings],
        )

    def to_store_content(self) -> str:
        """Serialise to the JSON stored in the ``folio_items_table`` content column."""
        return self.model_dump_json()

    @classmethod
    def from_store_content(cls, content: str) -> FolioEnrichedInstance:
        """Parse a row's stored ``content`` back into an instance."""
        return cls.model_validate_json(content)


def _opt_str(value: Any | None) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
