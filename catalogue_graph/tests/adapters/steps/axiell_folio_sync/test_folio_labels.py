"""The labels the sync puts on its FOLIO records for staff.

The AxC GUID is the instance id and is written nowhere else: neither the
holdings nor the item carries ``formerIds`` (the FOLIO transformer reads an
item's as its Sierra item number). The instance carries the ``Synched by Axiell`` instance status that
marks it as synced. Neither is how the sync finds a record; see test_folio_ids.
"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

import pytest

from adapters.steps.axiell_folio_sync.folio import RefCache
from adapters.steps.axiell_folio_sync.mapping import (
    SYNCED_INSTANCE_STATUS,
    MappingError,
    select_and_build,
)

from .test_item_mapping import FakeRefCache, _item_record

GUID = "00000000-0000-4000-8000-000000000001"


def test_the_instance_id_is_the_guid() -> None:
    mapped = select_and_build(_item_record(), FakeRefCache())  # type: ignore[arg-type]
    assert mapped is not None

    assert mapped.instance.id == GUID


def test_neither_the_holdings_nor_the_item_carries_former_ids() -> None:
    """The FOLIO transformer reads an item's formerIds as its Sierra item
    number and fails on anything else, and the holdings needs no copy of the
    GUID, since it is the instance id."""
    mapped = select_and_build(_item_record(), FakeRefCache())  # type: ignore[arg-type]
    assert mapped is not None

    assert "formerIds" not in mapped.holdings.model_dump(exclude_none=True)
    assert "formerIds" not in mapped.item.model_dump(exclude_none=True)


def test_the_instance_carries_the_synced_instance_status() -> None:
    asked: list[str | None] = []

    class RecordingRefCache(FakeRefCache):
        def resolve_instance_status(self, term: str | None) -> str | None:
            asked.append(term)
            return "status-uuid"

    mapped = select_and_build(_item_record(), RecordingRefCache())  # type: ignore[arg-type]
    assert mapped is not None

    assert mapped.instance.statusId == "status-uuid"
    assert asked == [SYNCED_INSTANCE_STATUS]


def test_no_record_carries_a_statistical_code_as_the_marker() -> None:
    """The item's codes are its access code alone (none for this record), and
    the instance and holdings carry none."""
    mapped = select_and_build(_item_record(), FakeRefCache())  # type: ignore[arg-type]
    assert mapped is not None

    assert "statisticalCodeIds" not in mapped.instance.model_dump(exclude_none=True)
    assert "statisticalCodeIds" not in mapped.holdings.model_dump(exclude_none=True)
    assert mapped.item.statisticalCodeIds == []


def test_a_tenant_without_the_instance_status_fails_the_record() -> None:
    class NoStatusRefCache(FakeRefCache):
        def resolve_instance_status(self, term: str | None) -> str | None:
            return None

    with pytest.raises(MappingError, match="instance status 'Synched by Axiell'"):
        select_and_build(_item_record(), NoStatusRefCache())  # type: ignore[arg-type]


def test_the_ref_cache_matches_statistical_codes_on_their_code() -> None:
    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/statistical-codes":
                return {
                    "statisticalCodes": [
                        {
                            "id": "online-uuid",
                            "code": "ONLINE REQUEST",
                            "name": "Online",
                        },
                        {"id": "open-uuid", "code": "OS", "name": "OPEN SHELVES"},
                    ]
                }
            if path == "/instance-types":
                return {"instanceTypes": [{"id": "text-uuid", "name": "text"}]}
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("the cache only reads")

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("the cache only reads")

        def delete(self, path: str) -> dict[str, Any]:
            raise AssertionError("the cache only reads")

    cache = RefCache(FakeInventory()).load()

    assert cache.resolve_statistical_code("ONLINE REQUEST") == "online-uuid"
    assert cache.resolve_statistical_code("online request") == "online-uuid"
    assert cache.resolve_statistical_code("Online") is None


def test_the_ref_cache_matches_instance_statuses_on_name_or_code() -> None:
    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/instance-statuses":
                return {
                    "instanceStatuses": [
                        {
                            "id": "axiell-uuid",
                            "name": "Synched by Axiell",
                            "code": "axiell",
                        },
                        {"id": "cat-uuid", "name": "Cataloged", "code": "cat"},
                    ]
                }
            if path == "/instance-types":
                return {"instanceTypes": [{"id": "text-uuid", "name": "text"}]}
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("the cache only reads")

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("the cache only reads")

        def delete(self, path: str) -> dict[str, Any]:
            raise AssertionError("the cache only reads")

    cache = RefCache(FakeInventory()).load()

    assert cache.resolve_instance_status("Synched by Axiell") == "axiell-uuid"
    assert cache.resolve_instance_status("synched by axiell") == "axiell-uuid"
    assert cache.resolve_instance_status("AXIELL") == "axiell-uuid"
    assert cache.resolve_instance_status("Uncataloged") is None
