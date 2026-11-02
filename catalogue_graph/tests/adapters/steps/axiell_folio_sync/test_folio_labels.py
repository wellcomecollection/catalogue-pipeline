"""The labels the sync puts on its FOLIO records for staff.

The AxC GUID goes in ``formerIds`` on the holdings and item (the instance id is
the GUID itself), and every record carries the ``AXIELL`` statistical code that
marks it as synced. Neither is how the sync finds a record; see test_folio_ids.
"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

import pytest

from adapters.steps.axiell_folio_sync.folio import RefCache
from adapters.steps.axiell_folio_sync.mapping import (
    SYNCED_STATISTICAL_CODE,
    MappingError,
    select_and_build,
)

from .test_item_mapping import FakeRefCache, _item_record

GUID = "00000000-0000-4000-8000-000000000001"


def test_holdings_and_item_carry_the_guid_in_former_ids() -> None:
    mapped = select_and_build(_item_record(), FakeRefCache())  # type: ignore[arg-type]
    assert mapped is not None

    assert mapped.holdings.formerIds == [GUID]
    assert mapped.item.formerIds == [GUID]


def test_the_former_id_is_the_guid_in_its_canonical_spelling() -> None:
    record = _item_record().replace(GUID, f"  {GUID.upper()}  ")
    mapped = select_and_build(record, FakeRefCache())  # type: ignore[arg-type]
    assert mapped is not None

    assert mapped.holdings.formerIds == [GUID]
    assert mapped.item.formerIds == [GUID]


def test_every_record_carries_the_synced_marker() -> None:
    mapped = select_and_build(_item_record(), FakeRefCache())  # type: ignore[arg-type]
    assert mapped is not None

    for payload in (mapped.instance, mapped.holdings, mapped.item):
        assert payload.statisticalCodeIds == ["statcode-uuid"]


def test_the_marker_is_resolved_from_its_code() -> None:
    asked: list[str | None] = []

    class RecordingRefCache(FakeRefCache):
        def resolve_statistical_code(self, code: str | None) -> str | None:
            asked.append(code)
            return "statcode-uuid"

    select_and_build(_item_record(), RecordingRefCache())  # type: ignore[arg-type]

    assert asked and set(asked) == {SYNCED_STATISTICAL_CODE}


def test_a_tenant_without_the_marker_code_fails_the_record() -> None:
    class NoMarkerRefCache(FakeRefCache):
        def resolve_statistical_code(self, code: str | None) -> str | None:
            return None

    with pytest.raises(MappingError, match="statistical code 'AXIELL'"):
        select_and_build(_item_record(), NoMarkerRefCache())  # type: ignore[arg-type]


def test_the_ref_cache_matches_statistical_codes_on_their_code() -> None:
    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/statistical-codes":
                return {
                    "statisticalCodes": [
                        {"id": "axiell-uuid", "code": "AXIELL", "name": "Synced"},
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

    assert cache.resolve_statistical_code("AXIELL") == "axiell-uuid"
    assert cache.resolve_statistical_code("axiell") == "axiell-uuid"
    assert cache.resolve_statistical_code("Synced") is None
