import json

import pytest

from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedInstance,
    FolioInventoryRecord,
)
from tests.test_utils import load_json_fixture

# A live response captured for wellcomecollection/platform#6581: a serial with 27
# holdings records and 61 items, barcodes replaced with placeholders.
FIXTURE = "folio/items_and_holdings_64125966.json"
INSTANCE_ID = "64125966-52e5-5a7f-9572-0bd80ea6980e"


def _parsed() -> FolioEnrichedInstance:
    return FolioEnrichedInstance.from_store_content(
        FolioInventoryRecord(data=load_json_fixture(FIXTURE)).to_store_content()
    )


def test_parses_live_response() -> None:
    instance = _parsed()

    assert instance.instance_id == INSTANCE_ID
    assert len(instance.items) == 61

    item = next(
        i for i in instance.items if i.id == "02738895-598f-5d5b-9b10-363d66da01f0"
    )
    assert item.volume == "Vol. 23 1969"
    assert item.enumeration is None


def test_store_content_keeps_the_whole_record() -> None:
    record = load_json_fixture(FIXTURE)
    assert json.loads(FolioInventoryRecord(data=record).to_store_content()) == record


def test_store_content_is_independent_of_key_order() -> None:
    record = {"instanceId": "i-1", "items": [{"id": "a", "volume": "v. 1"}]}
    reordered = {"items": [{"volume": "v. 1", "id": "a"}], "instanceId": "i-1"}
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
    assert (
        FolioInventoryRecord(
            data={"instanceId": "i-1", "items": [{"id": "a"}]}
        ).instance_id
        == "i-1"
    )


def test_record_raises_when_item_missing_id() -> None:
    """An item with no id cannot mint a stable identifier, so the batch fails."""
    with pytest.raises(ValueError, match="missing an id"):
        FolioInventoryRecord(data={"instanceId": "i-1", "items": [{"barcode": "123"}]})


def test_record_repr_shows_the_instance_id_not_the_record() -> None:
    record = FolioInventoryRecord(data={"instanceId": "i-1", "items": [{"id": "a"}]})
    assert repr(record) == "FolioInventoryRecord(instance_id='i-1')"


def test_record_raises_when_instance_missing_id() -> None:
    """A record with no instanceId cannot be keyed to a bib row, so the batch fails."""
    with pytest.raises(ValueError, match="missing instanceId"):
        FolioInventoryRecord(data={"items": [{"id": "item-1"}]})
