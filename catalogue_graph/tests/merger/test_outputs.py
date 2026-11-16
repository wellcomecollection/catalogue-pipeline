"""The identified to merged transition: availabilities from locations, and the merged document."""

from datetime import UTC, datetime

import pytest

from ingestor.models.merged.work import (
    DeletedMergedWork,
    InvisibleMergedWork,
    MergedWork,
    RedirectedMergedWork,
    VisibleMergedWork,
)
from ingestor.models.shared.deleted_reason import DeletedFromSource
from ingestor.models.shared.invisible_reason import InvisibleReason
from merger.availabilities import availabilities
from merger.outputs import to_merged_work
from models.pipeline.access_condition import AccessCondition
from models.pipeline.access_method import OnlineRequest, ViewOnline
from models.pipeline.access_status import (
    AccessStatus,
    Closed,
    LicensedRelatedResource,
    LicensedResource,
    Open,
)
from models.pipeline.availability import CLOSED_STORES, ONLINE, OPEN_SHELVES
from models.pipeline.holdings import Holdings
from models.pipeline.id_label import Id, IdLabel
from models.pipeline.identified.work import (
    DeletedIdentifiedWork,
    IdentifiedMergeCandidate,
    IdentifiedWork,
    IdentifiedWorkState,
    InvisibleIdentifiedWork,
    RedirectedIdentifiedWork,
    VisibleIdentifiedWork,
)
from models.pipeline.identifier import Identified, SourceIdentifier, Unidentifiable
from models.pipeline.item import Item
from models.pipeline.location import (
    ClosedStores,
    DigitalLocation,
    LocationType,
    OnExhibition,
    OnlineResource,
    OpenShelves,
    PhysicalLocation,
)
from models.pipeline.note import Note
from models.pipeline.work_data import WorkData

MERGED_AT = datetime(2026, 11, 11, 9, 30, tzinfo=UTC)


def physical(location_type: LocationType) -> PhysicalLocation:
    return PhysicalLocation(
        location_type=location_type,
        label=location_type.id,
        access_conditions=[AccessCondition(method=OnlineRequest, status=Open)],
    )


def digital(status: AccessStatus | None) -> DigitalLocation:
    return DigitalLocation(
        location_type=OnlineResource,
        url="https://example.org/work",
        access_conditions=[AccessCondition(method=ViewOnline, status=status)],
    )


def item(*locations: PhysicalLocation | DigitalLocation) -> Item:
    return Item(id=Unidentifiable(), locations=list(locations))


def note(note_type: str, contents: str) -> Note:
    return Note(note_type=IdLabel(id=note_type, label=note_type), contents=contents)


def closed_stores_with(*notes: Note) -> WorkData:
    return WorkData(items=[item(physical(ClosedStores))], notes=list(notes))


@pytest.mark.parametrize(
    ("data", "expected"),
    [
        pytest.param(WorkData(), [], id="no locations"),
        pytest.param(closed_stores_with(), [CLOSED_STORES], id="closed stores"),
        pytest.param(
            closed_stores_with(note("terms-of-use", "Closed until 1 January 2050.")),
            [CLOSED_STORES],
            id="closed stores with ordinary terms of use",
        ),
        pytest.param(
            closed_stores_with(
                note("terms-of-use", "Available at the Royal College of Physicians.")
            ),
            [],
            id="closed stores held elsewhere, by 'available at'",
        ),
        pytest.param(
            closed_stores_with(
                note("terms-of-use", "Available by appointment at the Wiener Library.")
            ),
            [],
            id="closed stores held elsewhere, by 'available by appointment at'",
        ),
        pytest.param(
            closed_stores_with(
                note(
                    "terms-of-use", "The papers are held by Churchill Archives Centre."
                )
            ),
            [],
            id="closed stores held elsewhere, by a named institution",
        ),
        pytest.param(
            closed_stores_with(
                note("general-note", "Available at the Royal College of Physicians.")
            ),
            [CLOSED_STORES],
            id="'available at' only counts in terms of use",
        ),
        pytest.param(
            WorkData(items=[item(physical(OpenShelves))]),
            [OPEN_SHELVES],
            id="open shelves",
        ),
        pytest.param(
            WorkData(
                items=[item(physical(OpenShelves))],
                notes=[note("terms-of-use", "Available at the Wiener Library.")],
            ),
            [OPEN_SHELVES],
            id="open shelves are not affected by the note",
        ),
        pytest.param(
            WorkData(items=[item(physical(OnExhibition))]),
            [],
            id="other physical locations",
        ),
        pytest.param(WorkData(items=[item(digital(Open))]), [ONLINE], id="open online"),
        pytest.param(
            WorkData(items=[item(digital(LicensedResource))]),
            [ONLINE],
            id="a licensed resource is the content itself",
        ),
        pytest.param(
            WorkData(items=[item(digital(LicensedRelatedResource))]),
            [],
            id="a licensed related resource only points at a page about the work",
        ),
        pytest.param(WorkData(items=[item(digital(Closed))]), [], id="closed online"),
        pytest.param(
            WorkData(items=[item(digital(None))]), [], id="online with no status"
        ),
        pytest.param(
            WorkData(holdings=[Holdings(location=physical(ClosedStores))]),
            [CLOSED_STORES],
            id="holdings count too",
        ),
        pytest.param(
            WorkData(
                items=[
                    item(digital(Open), physical(ClosedStores)),
                    item(physical(ClosedStores)),
                ]
            ),
            [ONLINE, CLOSED_STORES],
            id="each availability once, in location order",
        ),
    ],
)
def test_availabilities(data: WorkData, expected: list[Id]) -> None:
    assert availabilities(data) == expected


def source_identifier(
    value: str, identifier_type: str = "sierra-system-number"
) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type=Id(id=identifier_type), ontology_type="Work", value=value
    )


def identified_state() -> IdentifiedWorkState:
    return IdentifiedWorkState(
        source_identifier=source_identifier("b12345678"),
        canonical_id="abcdefgh",
        source_modified_time="2026-11-10T08:00:00Z",
        merge_candidates=[
            IdentifiedMergeCandidate(
                id=Identified(
                    source_identifier=source_identifier("b12345678", "mets"),
                    canonical_id="hgfedcba",
                ),
                reason="METS work",
            )
        ],
    )


def test_to_merged_work_keeps_the_document_and_derives_the_state() -> None:
    work = VisibleIdentifiedWork(
        version=3,
        data=WorkData(
            title="A work", items=[item(physical(ClosedStores), digital(Open))]
        ),
        state=identified_state(),
    )

    merged = to_merged_work(work, MERGED_AT)

    assert isinstance(merged, VisibleMergedWork)
    assert merged.version == 3
    assert merged.data == work.data
    assert merged.state.source_identifier == work.state.source_identifier
    assert merged.state.canonical_id == "abcdefgh"
    assert merged.state.source_modified_time == "2026-11-10T08:00:00Z"
    assert merged.state.merged_time == "2026-11-11T09:30:00Z"
    assert merged.state.availabilities == [CLOSED_STORES, ONLINE]
    assert merged.state.merge_candidates == work.state.merge_candidates


REDIRECT_TARGET = Identified(
    source_identifier=source_identifier("b87654321"), canonical_id="zyxwvuts"
)


@pytest.mark.parametrize(
    ("work", "merged_type"),
    [
        pytest.param(
            VisibleIdentifiedWork(version=1, state=identified_state()),
            VisibleMergedWork,
            id="Visible",
        ),
        pytest.param(
            InvisibleIdentifiedWork(
                version=1,
                state=identified_state(),
                invisibility_reasons=[InvisibleReason(type="SourceFieldMissing")],
            ),
            InvisibleMergedWork,
            id="Invisible",
        ),
        pytest.param(
            DeletedIdentifiedWork(
                version=1, state=identified_state(), deleted_reason=DeletedFromSource()
            ),
            DeletedMergedWork,
            id="Deleted",
        ),
        pytest.param(
            RedirectedIdentifiedWork(
                version=1, state=identified_state(), redirect_target=REDIRECT_TARGET
            ),
            RedirectedMergedWork,
            id="Redirected",
        ),
    ],
)
def test_to_merged_work_keeps_the_work_type(
    work: IdentifiedWork, merged_type: type[MergedWork]
) -> None:
    merged = to_merged_work(work, MERGED_AT)

    assert type(merged) is merged_type
    assert merged.type == work.type


# A document as the id minter writes it to the works_identified table.
RAW_STATE = {
    "sourceIdentifier": {
        "identifierType": {"id": "sierra-system-number"},
        "ontologyType": "Work",
        "value": "b12345678",
    },
    "canonicalId": "abcdefgh",
    "sourceModifiedTime": "2026-11-10T08:00:00Z",
    "relations": {"ancestors": []},
    "mergeCandidates": [
        {
            "id": {
                "canonicalId": "hgfedcba",
                "sourceIdentifier": {
                    "identifierType": {"id": "mets"},
                    "ontologyType": "Work",
                    "value": "b12345678",
                },
                "otherIdentifiers": [],
                "type": "Identified",
            },
            "reason": "METS work",
        }
    ],
}

RAW_DATA = {
    "title": "A work",
    "items": [
        {
            "id": {"type": "Unidentifiable"},
            "locations": [
                {
                    "type": "PhysicalLocation",
                    "locationType": {"id": "closed-stores"},
                    "label": "Closed stores",
                    "accessConditions": [
                        {
                            "method": {"type": "OnlineRequest"},
                            "status": {"type": "Open"},
                        }
                    ],
                },
                {
                    "type": "DigitalLocation",
                    "locationType": {"id": "online-resource"},
                    "url": "https://example.org/work",
                    "accessConditions": [
                        {
                            "method": {"type": "ViewOnline"},
                            "status": {
                                "type": "LicensedResources",
                                "relationship": {"type": "Resource"},
                            },
                        }
                    ],
                },
            ],
        }
    ],
    "notes": [
        {
            "noteType": {"id": "terms-of-use", "label": "Terms of use"},
            "contents": "Open.",
        }
    ],
}


def test_from_raw_document_reads_a_visible_work() -> None:
    work = IdentifiedWork.from_raw_document(
        {"type": "Visible", "version": 3, "data": RAW_DATA, "state": RAW_STATE}
    )

    assert isinstance(work, VisibleIdentifiedWork)
    assert work.version == 3
    assert work.data.title == "A work"
    assert work.state.canonical_id == "abcdefgh"
    assert work.state.source_identifier == source_identifier("b12345678")
    assert [c.id.canonical_id for c in work.state.merge_candidates] == ["hgfedcba"]
    [closed, online] = work.data.items[0].locations
    assert closed == physical(ClosedStores).model_copy(
        update={"label": "Closed stores"}
    )
    assert online == digital(LicensedResource)
    assert availabilities(work.data) == [CLOSED_STORES, ONLINE]


@pytest.mark.parametrize(
    ("document", "work_type"),
    [
        pytest.param(
            {
                "type": "Invisible",
                "invisibilityReasons": [
                    {"type": "SourceFieldMissing", "info": "No title"}
                ],
            },
            InvisibleIdentifiedWork,
            id="Invisible",
        ),
        pytest.param(
            {"type": "Deleted", "deletedReason": {"type": "DeletedFromSource"}},
            DeletedIdentifiedWork,
            id="Deleted",
        ),
        pytest.param(
            {
                "type": "Redirected",
                "redirectTarget": {
                    "canonicalId": "zyxwvuts",
                    "sourceIdentifier": {
                        "identifierType": {"id": "sierra-system-number"},
                        "ontologyType": "Work",
                        "value": "b87654321",
                    },
                    "otherIdentifiers": [],
                    "type": "Identified",
                },
            },
            RedirectedIdentifiedWork,
            id="Redirected",
        ),
    ],
)
def test_from_raw_document_reads_the_other_work_types(
    document: dict, work_type: type[IdentifiedWork]
) -> None:
    work = IdentifiedWork.from_raw_document(
        {**document, "version": 1, "state": RAW_STATE}
    )

    assert type(work) is work_type
    assert work.state.canonical_id == "abcdefgh"


def test_from_raw_document_rejects_an_unknown_type() -> None:
    with pytest.raises(ValueError, match="Unknown work type 'Merged'"):
        IdentifiedWork.from_raw_document(
            {"type": "Merged", "version": 1, "state": RAW_STATE}
        )
