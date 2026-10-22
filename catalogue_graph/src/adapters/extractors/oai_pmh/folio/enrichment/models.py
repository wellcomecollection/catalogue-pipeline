"""Models for the FOLIO items-and-holdings enrichment payload.

The FOLIO OAI-PMH feed (``marc21_withholdings``) carries item data in MARC 952
datafields but no item UUID, and few of the fields the item mappings need. Each
harvested instance is enriched from mod-inventory-storage's
``POST /inventory-hierarchy/items-and-holdings`` endpoint (chosen in
wellcomecollection/platform#6581).

Each response record is ``{"instanceId", "source", "modeOfIssuance",
"natureOfContent", "holdings": [...], "items": [...]}`` with camelCase keys, ids
already resolved to names and codes, and nulls stripped. The enrichment stores each
record verbatim, so a mapping that needs another field can read it without a
re-fetch.

``FolioInventoryRecord`` is the write side: it checks the ids the store depends
on and serialises the record. ``FolioEnrichedInstance`` and ``FolioEnrichedItem``
are the read side, parsed from the stored record at transform time.

Only the fields the transform reads are modelled; add a field alongside the code
that uses it. The view doesn't return ``hrId``, staff-only notes or
``hrid`` will be returned by the view once https://github.com/folio-org/mod-inventory-storage/pull/1382 is released.
"""

from __future__ import annotations

import json
from typing import Annotated, Any

from pydantic import (
    AfterValidator,
    AliasPath,
    BaseModel,
    ConfigDict,
    Field,
    ValidationError,
    computed_field,
    model_validator,
)
from pydantic.alias_generators import to_camel


def _strip_to_none(value: str) -> str | None:
    """Trim, and treat blank as missing, so a blank field never stands in for a real one."""
    return value.strip() or None


StrippedText = Annotated[str, AfterValidator(_strip_to_none)]


class FolioModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class FolioLocation(FolioModel):
    """A resolved FOLIO location. ``name`` is the discovery display name, falling back to the name."""

    code: str
    name: StrippedText | None = None
    library_name: StrippedText | None = None


class FolioStatisticalCode(FolioModel):
    code: str


class FolioItemNote(FolioModel):
    item_note_type_name: str | None = None
    note: StrippedText | None = None


class FolioEnrichedItem(FolioModel):
    """A single FOLIO item, keyed by its inventory UUID.

    ``id`` is the load-bearing field: it is the stable identifier the OAI-PMH 952
    datafield cannot provide.
    """

    id: str
    volume: StrippedText | None = None
    enumeration: StrippedText | None = None
    status: StrippedText | None = None
    # FOLIO resolves the effective location from temporary, permanent, then holdings.
    effective_location: FolioLocation | None = Field(
        default=None, validation_alias=AliasPath("location", "location")
    )
    # The effective call number, which FOLIO falls back to the holdings record for.
    call_number: StrippedText | None = Field(
        default=None, validation_alias=AliasPath("callNumber", "callNumber")
    )
    statistical_codes: list[FolioStatisticalCode] = Field(default_factory=list)
    permanent_loan_type: StrippedText | None = None
    temporary_loan_type: StrippedText | None = None
    material_type: StrippedText | None = None
    notes: list[FolioItemNote] = Field(default_factory=list)
    suppress_from_discovery: bool = False

    @property
    def display_note(self) -> str | None:
        notes = [
            n.note
            for n in self.notes
            if n.item_note_type_name == "Display note" and n.note
        ]
        return "\n\n".join(notes) or None


class FolioEnrichedInstance(FolioModel):
    """Items for a single FOLIO instance, parsed from its stored record."""

    instance_id: str
    items: list[FolioEnrichedItem] = Field(default_factory=list)

    @classmethod
    def from_store_content(cls, content: str) -> FolioEnrichedInstance:
        """Parse a row's stored ``content`` (the raw API record)."""
        return cls.model_validate_json(content)


class FolioInventoryRecord(BaseModel):
    """A raw items-and-holdings record as stored, validated on construction.

    Only the ids the store depends on are checked: a record without an instance
    id cannot be keyed to a bib row, an item without an id cannot mint a stable
    identifier, and an item must link to a holdings record in the same response
    for the transform to fall back to it. A record that fails is rejected on its
    own (see ``parse_inventory_records``). Everything else is stored as it comes
    and parsed at transform time.
    """

    model_config = ConfigDict(frozen=True)

    # Wrapped rather than modelled, so the stored record is exactly what FOLIO sent.
    data: dict[str, Any] = Field(repr=False)

    @model_validator(mode="after")
    def _check_ids(self) -> FolioInventoryRecord:
        # Anything malformed raises ValueError, so pydantic turns it into a
        # rejection of this record rather than an error that aborts the fetch.
        instance_id = self.data.get("instanceId")
        if not instance_id:
            raise ValueError("FOLIO items-and-holdings record is missing instanceId")
        if not isinstance(instance_id, str):
            raise ValueError(
                "FOLIO items-and-holdings record has a non-string instanceId"
            )

        items = self._objects("items")
        for item in items:
            self._check_id(item.get("id"), "item")

        holdings_ids: set[str] = set()
        for holdings in self._objects("holdings"):
            holdings_id = self._check_id(holdings.get("id"), "holdings")
            if holdings_id in holdings_ids:
                raise ValueError(
                    f"FOLIO holdings {holdings_id!r} appears twice on instance "
                    f"{self.instance_id!r}"
                )
            holdings_ids.add(holdings_id)

        for item in items:
            link = item.get("holdingsRecordId")
            if not isinstance(link, str) or link not in holdings_ids:
                raise ValueError(
                    f"FOLIO item {item['id']!r} on instance {self.instance_id!r} "
                    "links to no holdings record in the response"
                )
        return self

    def _objects(self, key: str) -> list[dict[str, Any]]:
        value = self.data.get(key) or []
        if not isinstance(value, list) or not all(isinstance(v, dict) for v in value):
            raise ValueError(
                f"FOLIO {key} on instance {self.instance_id!r} is not a list of objects"
            )
        return value

    def _check_id(self, value: Any, kind: str) -> str:
        if not value:
            raise ValueError(
                f"FOLIO {kind} on instance {self.instance_id!r} is missing an id"
            )
        if not isinstance(value, str):
            raise ValueError(
                f"FOLIO {kind} on instance {self.instance_id!r} has a non-string id"
            )
        return value

    @computed_field  # type: ignore[prop-decorator]
    @property
    def instance_id(self) -> str:
        return str(self.data["instanceId"])

    def to_store_content(self) -> str:
        """Serialise for the store's ``content`` column.

        Keys are sorted so an unchanged record serialises identically and
        ``incremental_update`` skips it.
        """
        return json.dumps(self.data, sort_keys=True, separators=(",", ":"))


class RejectedInventoryRecord(BaseModel):
    """A response record left out of the store, and why."""

    instance_id: str | None
    reason: str


class InventoryFetch(BaseModel):
    """The records from an items-and-holdings fetch, split into stored and rejected."""

    records: list[FolioInventoryRecord] = Field(default_factory=list)
    rejected: list[RejectedInventoryRecord] = Field(default_factory=list)


def parse_inventory_records(raw: list[dict[str, Any]]) -> InventoryFetch:
    """Validate each raw record on its own, so one bad record doesn't hold up the rest.

    A rejected instance keeps its last stored row, and is fetched again once a fix
    in FOLIO advances its OAI datestamp.
    """
    fetch = InventoryFetch()
    for data in raw:
        try:
            fetch.records.append(FolioInventoryRecord(data=data))
        except ValidationError as e:
            reason = str(e.errors()[0]["msg"]).removeprefix("Value error, ")
            instance_id = data.get("instanceId") if isinstance(data, dict) else None
            if not isinstance(instance_id, str):
                instance_id = None
            fetch.rejected.append(
                RejectedInventoryRecord(instance_id=instance_id, reason=reason)
            )
    return fetch
