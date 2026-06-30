from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedInstance,
)

# A representative enrichedInstances record. The exact contract is an open question,
# so the parser is deliberately tolerant; these tests pin the fields we rely on.
SAMPLE_RECORD = {
    "instanceId": "3144420c-1111-2222-3333-444455556666",
    "itemsandholdingsfields": {
        "items": [
            {
                "id": "aaa11111-0000-0000-0000-000000000001",
                "holdingsRecordId": "hhh00000-0000-0000-0000-000000000001",
                "barcode": "1234567890",
                "callNumber": "WZ 100",
                "copyNumber": "1",
                "materialType": "book",
                "permanentLocation": "Closed stores",
            },
            {
                "id": "aaa11111-0000-0000-0000-000000000002",
                "barcode": None,
            },
        ],
        "holdings": [
            {"id": "hhh00000-0000-0000-0000-000000000001", "callNumber": "WZ 100"}
        ],
    },
}


def test_from_api_parses_items_and_holdings() -> None:
    instance = FolioEnrichedInstance.from_api(SAMPLE_RECORD)

    assert instance.instance_id == "3144420c-1111-2222-3333-444455556666"
    assert [i.id for i in instance.items] == [
        "aaa11111-0000-0000-0000-000000000001",
        "aaa11111-0000-0000-0000-000000000002",
    ]
    first = instance.items[0]
    assert first.barcode == "1234567890"
    assert first.call_number == "WZ 100"
    assert first.copy_number == "1"
    assert first.location == "Closed stores"
    assert first.holdings_record_id == "hhh00000-0000-0000-0000-000000000001"

    # Missing fields are tolerated.
    assert instance.items[1].barcode is None
    assert instance.items[1].call_number is None

    assert [h.id for h in instance.holdings] == ["hhh00000-0000-0000-0000-000000000001"]


def test_from_api_handles_top_level_items() -> None:
    """Items/holdings supplied at the top level (not nested) are also parsed."""
    instance = FolioEnrichedInstance.from_api(
        {
            "instanceId": "i-1",
            "items": [{"id": "item-1"}],
            "holdings": [{"id": "hold-1"}],
        }
    )
    assert [i.id for i in instance.items] == ["item-1"]
    assert [h.id for h in instance.holdings] == ["hold-1"]


def test_from_api_handles_no_items() -> None:
    instance = FolioEnrichedInstance.from_api({"instanceId": "i-empty"})
    assert instance.instance_id == "i-empty"
    assert instance.items == []
    assert instance.holdings == []


def test_store_content_round_trips() -> None:
    instance = FolioEnrichedInstance.from_api(SAMPLE_RECORD)
    content = instance.to_store_content()
    assert isinstance(content, str)

    restored = FolioEnrichedInstance.from_store_content(content)
    assert restored == instance
    assert restored.items[0].id == "aaa11111-0000-0000-0000-000000000001"
