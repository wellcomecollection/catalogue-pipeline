"""The FOLIO record ids the sync derives from the AxC GUID (MARC 001).

The ids are how the sync finds its records again, on every later run and in the
deletion cascade, so they must be stable, distinct per entity, and valid UUIDs.
"""

from __future__ import annotations

import uuid
from collections.abc import Mapping
from typing import Any

import pytest

from adapters.steps.axiell_folio_sync.mapping import (
    HOLDINGS_ID_NAME,
    ITEM_ID_NAME,
    MappingError,
    _holdings_id,
    _instance_id,
    _item_id,
    select_and_build,
)
from adapters.steps.axiell_folio_sync.upsert import suppress_by_guid

from .test_item_mapping import FakeRefCache, _item_record

GUID = "3fe75c01-9eb1-47b4-ac50-0772aa1c11f9"


def test_the_entity_names_are_pinned() -> None:
    """Changing an entity name orphans every holdings or item already in FOLIO.
    This test exists so that a change has to be made on purpose."""
    assert HOLDINGS_ID_NAME == "holdings"
    assert ITEM_ID_NAME == "item"


def test_the_instance_id_is_the_guid() -> None:
    assert _instance_id(GUID) == GUID


def test_holdings_and_item_ids_are_uuid5_in_the_guid() -> None:
    assert _holdings_id(GUID) == str(uuid.uuid5(uuid.UUID(GUID), "holdings"))
    assert _item_id(GUID) == str(uuid.uuid5(uuid.UUID(GUID), "item"))
    assert uuid.UUID(_holdings_id(GUID)).version == 5


def test_ids_are_stable_across_calls() -> None:
    assert _instance_id(GUID) == _instance_id(GUID)


def test_each_entity_gets_its_own_id() -> None:
    ids = {_instance_id(GUID), _holdings_id(GUID), _item_id(GUID)}
    assert len(ids) == 3


def test_different_guids_get_different_ids() -> None:
    other = "960bb5d0-6635-41d4-bff7-f9618338274c"
    assert _instance_id(GUID) != _instance_id(other)


def test_case_and_padding_of_the_guid_do_not_change_the_id() -> None:
    for id_of in (_instance_id, _holdings_id, _item_id):
        assert id_of(f"  {GUID.upper()}  ") == id_of(GUID)


def test_a_001_that_is_not_a_uuid_fails_the_record() -> None:
    record = _item_record().replace(
        "00000000-0000-4000-8000-000000000001", "not-a-guid"
    )
    with pytest.raises(MappingError, match="not a UUID"):
        select_and_build(record, FakeRefCache())  # type: ignore[arg-type]


def test_the_builders_set_the_ids_and_parent_links() -> None:
    mapped = select_and_build(_item_record(), FakeRefCache())  # type: ignore[arg-type]
    assert mapped is not None

    assert mapped.instance.id == _instance_id("00000000-0000-4000-8000-000000000001")
    assert mapped.holdings.id == _holdings_id("00000000-0000-4000-8000-000000000001")
    assert mapped.holdings.instanceId == mapped.instance.id
    assert mapped.item.id == _item_id("00000000-0000-4000-8000-000000000001")
    assert mapped.item.holdingsRecordId == mapped.holdings.id


def test_the_payloads_carry_no_hrid() -> None:
    mapped = select_and_build(_item_record(), FakeRefCache())  # type: ignore[arg-type]
    assert mapped is not None

    for payload in (mapped.instance, mapped.holdings, mapped.item):
        assert "hrid" not in payload.model_dump()


def test_the_deletion_cascade_looks_up_the_derived_ids() -> None:
    queries: list[tuple[str, Any]] = []

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            queries.append((path, (params or {}).get("query")))
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("the cascade must not POST")

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("nothing exists, so nothing is suppressed")

        def delete(self, path: str) -> dict[str, Any]:
            raise AssertionError("suppress must not delete")

    suppress_by_guid(GUID, FakeInventory(), dry_run=False)

    assert queries == [
        ("/inventory/items", f'id=="{_item_id(GUID)}"'),
        ("/holdings-storage/holdings", f'id=="{_holdings_id(GUID)}"'),
        ("/inventory/instances", f'id=="{_instance_id(GUID)}"'),
    ]
