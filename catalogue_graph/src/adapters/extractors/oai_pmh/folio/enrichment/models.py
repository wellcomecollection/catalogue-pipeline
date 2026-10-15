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
that uses it. The view never returns ``hrId``, staff-only notes or
``administrativeNotes``.
"""

from __future__ import annotations

import json
from typing import Annotated, Any

from pydantic import (
    AfterValidator,
    BaseModel,
    ConfigDict,
    Field,
    computed_field,
    model_validator,
)
from pydantic.alias_generators import to_camel


def _strip_to_none(value: str | None) -> str | None:
    # A blank value must not mask a fallback, e.g. enumeration over volume.
    return (value or "").strip() or None


OptionalText = Annotated[str | None, AfterValidator(_strip_to_none)]


class FolioModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class FolioEnrichedItem(FolioModel):
    """A single FOLIO item, keyed by its inventory UUID.

    ``id`` is the load-bearing field: it is the stable identifier the OAI-PMH 952
    datafield cannot provide.
    """

    id: str
    volume: OptionalText = None
    enumeration: OptionalText = None


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
    id cannot be keyed to a bib row, and an item without an id cannot mint a
    stable identifier, so either fails the whole batch. Everything else is stored
    as it comes and parsed at transform time.
    """

    model_config = ConfigDict(frozen=True)

    # Wrapped rather than modelled, so the stored record is exactly what FOLIO sent.
    data: dict[str, Any] = Field(repr=False)

    @model_validator(mode="after")
    def _check_ids(self) -> FolioInventoryRecord:
        if not self.data.get("instanceId"):
            raise ValueError("FOLIO items-and-holdings record is missing instanceId")
        for item in self.data.get("items") or []:
            if not item.get("id"):
                raise ValueError(
                    f"FOLIO item on instance {self.instance_id!r} is missing an id"
                )
        return self

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
