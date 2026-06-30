import pytest

from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedInstance,
)

# A record shaped per the mod-inventory-storage oai-pmh-view enrichedInstances docs:
# instanceId + itemsAndHoldingsFields.items, with nested callNumber / location objects.
SAMPLE_RECORD = {
    "instanceId": "3144420c-1111-2222-3333-444455556666",
    "itemsAndHoldingsFields": {
        "instanceid": "3144420c-1111-2222-3333-444455556666",
        "items": [
            {
                "id": "aaa11111-0000-0000-0000-000000000001",
                "volume": "v1",
                "enumeration": "no. 1",
                "materialType": "book",
                "barcode": "1234567890",
                "callNumber": {
                    "prefix": "WZ",
                    "suffix": None,
                    "typeId": "ttt-0000",
                    "callNumber": "WZ 100",
                },
                "location": {
                    "name": "Closed stores",
                    "location": {
                        "libraryName": "Rare Materials Room",
                        "institutionName": "Wellcome Collection",
                    },
                },
            },
            {
                "id": "aaa11111-0000-0000-0000-000000000002",
            },
        ],
    },
}


def test_from_api_parses_documented_shape() -> None:
    instance = FolioEnrichedInstance.from_api(SAMPLE_RECORD)

    assert instance.instance_id == "3144420c-1111-2222-3333-444455556666"
    assert [i.id for i in instance.items] == [
        "aaa11111-0000-0000-0000-000000000001",
        "aaa11111-0000-0000-0000-000000000002",
    ]
    first = instance.items[0]
    assert first.barcode == "1234567890"
    assert first.volume == "v1"
    assert first.enumeration == "no. 1"
    assert first.material_type == "book"
    # callNumber is a nested object; we flatten to its call-number string.
    assert first.call_number == "WZ 100"
    # location is a nested object; we take a readable name.
    assert first.location == "Closed stores"

    # Missing fields are tolerated.
    assert instance.items[1].barcode is None
    assert instance.items[1].call_number is None
    assert instance.items[1].location is None


def test_from_api_flattens_location_from_nested_library_name() -> None:
    instance = FolioEnrichedInstance.from_api(
        {
            "instanceId": "i-1",
            "itemsAndHoldingsFields": {
                "items": [
                    {
                        "id": "item-1",
                        "location": {
                            "location": {"libraryName": "History of Medicine"}
                        },
                    }
                ]
            },
        }
    )
    assert instance.items[0].location == "History of Medicine"


def test_from_api_handles_lowercase_wrapper_and_string_callnumber() -> None:
    """Tolerate the lowercase ``itemsandholdingsfields`` and a plain-string callNumber."""
    instance = FolioEnrichedInstance.from_api(
        {
            "instanceId": "i-1",
            "itemsandholdingsfields": {
                "items": [{"id": "item-1", "callNumber": "WZ 100"}]
            },
        }
    )
    assert [i.id for i in instance.items] == ["item-1"]
    assert instance.items[0].call_number == "WZ 100"


def test_from_api_handles_no_items() -> None:
    instance = FolioEnrichedInstance.from_api({"instanceId": "i-empty"})
    assert instance.instance_id == "i-empty"
    assert instance.items == []


def test_store_content_round_trips() -> None:
    instance = FolioEnrichedInstance.from_api(SAMPLE_RECORD)
    content = instance.to_store_content()
    assert isinstance(content, str)

    restored = FolioEnrichedInstance.from_store_content(content)
    assert restored == instance
    assert restored.items[0].id == "aaa11111-0000-0000-0000-000000000001"


def test_from_api_raises_when_item_missing_id() -> None:
    """An item with no id cannot mint a stable identifier, so parsing fails loudly
    rather than emitting an item identified by the literal string "None"."""
    with pytest.raises(ValueError, match="item is missing an id"):
        FolioEnrichedInstance.from_api(
            {
                "instanceId": "i-1",
                "itemsAndHoldingsFields": {"items": [{"barcode": "123"}]},
            }
        )


def test_from_api_raises_when_instance_missing_id() -> None:
    """A record with no instanceId cannot be keyed to a bib row, so parsing fails
    loudly rather than storing a row keyed by the literal string "None"."""
    with pytest.raises(ValueError, match="missing instanceId"):
        FolioEnrichedInstance.from_api(
            {"itemsAndHoldingsFields": {"items": [{"id": "item-1"}]}}
        )
