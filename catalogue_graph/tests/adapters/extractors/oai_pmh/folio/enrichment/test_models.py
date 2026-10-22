import json
from typing import Any

import pytest

from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedInstance,
    FolioInventoryRecord,
)
from tests.adapters.transformers.folio.helpers import fixture_record

INSTANCE_ID = "64125966-52e5-5a7f-9572-0bd80ea6980e"


def _parsed() -> FolioEnrichedInstance:
    return FolioEnrichedInstance.from_store_content(
        FolioInventoryRecord(data=fixture_record()).to_store_content()
    )


def test_parses_the_fixture() -> None:
    instance = _parsed()

    assert instance.instance_id == INSTANCE_ID
    assert len(instance.items) == 61

    item = next(
        i for i in instance.items if i.id == "02738895-598f-5d5b-9b10-363d66da01f0"
    )
    assert item.volume == "Vol. 23 1969"
    assert item.enumeration is None


def test_store_content_keeps_the_whole_record() -> None:
    record = fixture_record()
    assert json.loads(FolioInventoryRecord(data=record).to_store_content()) == record


def _reverse_keys(value: Any) -> Any:
    if isinstance(value, dict):
        return {k: _reverse_keys(value[k]) for k in reversed(value)}
    if isinstance(value, list):
        return [_reverse_keys(v) for v in value]
    return value


def test_store_content_is_independent_of_key_order() -> None:
    record = fixture_record()
    reordered = _reverse_keys(record)
    assert json.dumps(reordered) != json.dumps(record)
    assert (
        FolioInventoryRecord(data=record).to_store_content()
        == FolioInventoryRecord(data=reordered).to_store_content()
    )


def test_parses_record_with_no_items() -> None:
    instance = FolioEnrichedInstance.from_store_content(
        FolioInventoryRecord(
            data={"instanceId": "i-empty", "holdings": [], "items": []}
        ).to_store_content()
    )
    assert instance.instance_id == "i-empty"
    assert instance.items == []


def test_record_exposes_instance_id() -> None:
    assert FolioInventoryRecord(data=fixture_record()).instance_id == INSTANCE_ID


def test_record_raises_when_item_missing_id() -> None:
    """An item with no id cannot mint a stable identifier, so the batch fails."""
    record = fixture_record()
    del record["items"][3]["id"]
    with pytest.raises(ValueError, match="missing an id"):
        FolioInventoryRecord(data=record)


def test_record_repr_shows_the_instance_id_not_the_record() -> None:
    record = FolioInventoryRecord(data=fixture_record())
    assert repr(record) == f"FolioInventoryRecord(instance_id='{INSTANCE_ID}')"


def test_record_raises_when_instance_missing_id() -> None:
    """A record with no instanceId cannot be keyed to a bib row, so the batch fails."""
    record = fixture_record()
    del record["instanceId"]
    with pytest.raises(ValueError, match="missing instanceId"):
        FolioInventoryRecord(data=record)
