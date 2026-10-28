"""Steps for FOLIO items-and-holdings records, joined onto the MARC record at transform time."""

from __future__ import annotations

from typing import Any

import pytest
from pytest_bdd import given, parsers, then

from adapters.transformers.folio.access_conditions import notes
from models.pipeline.identifier import Identifiable
from models.pipeline.item import Item
from models.pipeline.location import PhysicalLocation
from models.pipeline.source.work import VisibleSourceWork

HOLDINGS_ID = "holdings-1"


@pytest.fixture
def folio_inventory() -> dict[str, Any]:
    return {
        "instanceId": "10000000-0000-0000-0000-000000000001",
        "holdings": [{"id": HOLDINGS_ID}],
        "items": [],
    }


def _location(code: str, name: str, library: str) -> dict[str, Any]:
    return {"location": {"code": code, "name": name, "libraryName": library}}


@given(parsers.parse('a FOLIO item "{item_id}"'), target_fixture="folio_item")
def folio_item(folio_inventory: dict[str, Any], item_id: str) -> dict[str, Any]:
    """An available, online-requestable book in closed stores, which steps then vary."""
    item: dict[str, Any] = {
        "id": item_id,
        "holdingsRecordId": HOLDINGS_ID,
        "status": "Available",
        "location": _location("sepbb", "Closed stores EPB / B", "Closed stores"),
        "callNumber": {"callNumber": "EPB/B/1"},
        "statisticalCodes": [{"code": "ONLINE REQUEST"}],
        "permanentLoanType": "Can circulate",
        "materialType": "book",
        "notes": [],
        "suppressFromDiscovery": False,
    }
    folio_inventory["items"].append(item)
    return item


@given(
    parsers.parse(
        'that item is in the location "{code}" named "{name}" in the library "{library}"'
    )
)
def item_location(
    folio_item: dict[str, Any], code: str, name: str, library: str
) -> None:
    folio_item["location"] = _location(code, name, library)


@given("that item has no location")
def item_no_location(folio_item: dict[str, Any]) -> None:
    # As items-and-holdings sends it when no location resolves.
    folio_item["location"] = {
        "location": {},
        "permanentLocation": {},
        "temporaryLocation": {},
    }


@given(parsers.parse('that item has the status "{status}"'))
def item_status(folio_item: dict[str, Any], status: str) -> None:
    folio_item["status"] = status


@given(parsers.parse('that item has the statistical code "{code}"'))
def item_statistical_code(folio_item: dict[str, Any], code: str) -> None:
    folio_item["statisticalCodes"] = [{"code": code}]


@given("that item has no statistical code")
def item_no_statistical_code(folio_item: dict[str, Any]) -> None:
    folio_item["statisticalCodes"] = []


@given(parsers.parse('that item has the loan type "{loan_type}"'))
def item_loan_type(folio_item: dict[str, Any], loan_type: str) -> None:
    folio_item["permanentLoanType"] = loan_type


@given(parsers.parse('that item has the temporary loan type "{loan_type}"'))
def item_temporary_loan_type(folio_item: dict[str, Any], loan_type: str) -> None:
    folio_item["temporaryLoanType"] = loan_type


@given(parsers.parse('that item has the material type "{material_type}"'))
def item_material_type(folio_item: dict[str, Any], material_type: str) -> None:
    folio_item["materialType"] = material_type


@given(parsers.parse('that item has the call number "{call_number}"'))
def item_call_number(folio_item: dict[str, Any], call_number: str) -> None:
    folio_item["callNumber"] = {"callNumber": call_number}


@given("that item has no call number")
def item_no_call_number(folio_item: dict[str, Any]) -> None:
    folio_item["callNumber"] = {}


@given(parsers.parse('that item has the display note "{note}"'))
def item_display_note(folio_item: dict[str, Any], note: str) -> None:
    folio_item["notes"].append({"itemNoteTypeName": "Display note", "note": note})


@given(parsers.parse('that item has the former ids "{former_ids}"'))
def item_former_ids(folio_item: dict[str, Any], former_ids: str) -> None:
    folio_item["formerIds"] = former_ids.split(", ")


@given("that item is suppressed from discovery")
def item_suppressed(folio_item: dict[str, Any]) -> None:
    folio_item["suppressFromDiscovery"] = True


def _item(work: VisibleSourceWork, item_id: str) -> Item:
    matching = [
        i
        for i in work.data.items
        if getattr(i.id, "source_identifier", None) is not None
        and i.id.source_identifier.value == item_id  # type: ignore[union-attr]
    ]
    assert len(matching) == 1, f"Expected one item {item_id!r}, got {work.data.items}"
    return matching[0]


def _location_of(work: VisibleSourceWork, item_id: str) -> PhysicalLocation:
    locations = _item(work, item_id).locations
    assert len(locations) == 1, f"Expected one location, got {locations}"
    location = locations[0]
    assert isinstance(location, PhysicalLocation)
    return location


@then(parsers.parse('the work has no item "{item_id}"'))
def no_item(work: VisibleSourceWork, item_id: str) -> None:
    assert all(
        getattr(i.id, "source_identifier", None) is None
        or i.id.source_identifier.value != item_id  # type: ignore[union-attr]
        for i in work.data.items
    )


def _identifiable(work: VisibleSourceWork, item_id: str) -> Identifiable:
    identifier = _item(work, item_id).id
    assert isinstance(identifier, Identifiable)
    return identifier


@then(parsers.parse('item "{item_id}" has the source identifier "{expected}"'))
def item_has_source_identifier(
    work: VisibleSourceWork, item_id: str, expected: str
) -> None:
    assert str(_identifiable(work, item_id).source_identifier) == expected


@then(parsers.parse('item "{item_id}" has the predecessor "{expected}"'))
def item_has_predecessor(work: VisibleSourceWork, item_id: str, expected: str) -> None:
    assert str(_identifiable(work, item_id).predecessor_identifier) == expected


@then(parsers.parse('item "{item_id}" has no predecessor'))
def item_has_no_predecessor(work: VisibleSourceWork, item_id: str) -> None:
    assert _identifiable(work, item_id).predecessor_identifier is None


@then(parsers.parse('item "{item_id}" has no location'))
def item_has_no_location(work: VisibleSourceWork, item_id: str) -> None:
    assert _item(work, item_id).locations == []


@then(
    parsers.parse(
        'item "{item_id}" has a location of type "{location_type}" labelled "{label}"'
    )
)
def item_has_location(
    work: VisibleSourceWork, item_id: str, location_type: str, label: str
) -> None:
    location = _location_of(work, item_id)
    assert (location.location_type.id, location.label) == (location_type, label)


@then(parsers.parse('item "{item_id}" has the shelfmark "{shelfmark}"'))
def item_has_shelfmark(work: VisibleSourceWork, item_id: str, shelfmark: str) -> None:
    assert _location_of(work, item_id).shelfmark == shelfmark


@then(parsers.parse('item "{item_id}" has no shelfmark'))
def item_has_no_shelfmark(work: VisibleSourceWork, item_id: str) -> None:
    assert _location_of(work, item_id).shelfmark is None


@then(
    parsers.re(
        r'item "(?P<item_id>[^"]+)" has the access method "(?P<method>\w+)",'
        r' (?:the status "(?P<status>\w+)"|no status)'
        r' and (?:the "(?P<note>[\w-]+)" note|no note)'
    )
)
def item_has_access(
    work: VisibleSourceWork,
    item_id: str,
    method: str,
    status: str | None,
    note: str | None,
) -> None:
    conditions = _location_of(work, item_id).access_conditions
    assert len(conditions) == 1, f"Expected one access condition, got {conditions}"
    condition = conditions[0]
    actual = (
        condition.method.type,
        condition.status.type if condition.status else None,
        condition.note,
    )
    assert actual == (method, status or None, notes()[note] if note else None)


@then(parsers.parse('item "{item_id}" has the access note "{note}"'))
def item_has_access_note(work: VisibleSourceWork, item_id: str, note: str) -> None:
    assert _location_of(work, item_id).access_conditions[0].note == note


@then(parsers.parse('item "{item_id}" has the note "{note}"'))
def item_has_note(work: VisibleSourceWork, item_id: str, note: str) -> None:
    assert _item(work, item_id).note == note


@then(parsers.parse('item "{item_id}" has no note'))
def item_has_no_note(work: VisibleSourceWork, item_id: str) -> None:
    assert _item(work, item_id).note is None
