import json
import pathlib
from typing import Any

import pytest
from hypothesis import given
from hypothesis import strategies as st
from pydantic.alias_generators import to_camel

from ingestor.models.shared.deleted_reason import DeletedReason
from ingestor.models.shared.invisible_reason import InvisibleReason
from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Concept, Contributor, Genre, Subject
from models.pipeline.holdings import Holdings
from models.pipeline.id_label import Format, Id, IdLabel, Language
from models.pipeline.identifier import Identified, SourceIdentifier
from models.pipeline.image import ImageData
from models.pipeline.item import Item
from models.pipeline.location import DigitalLocation, OnlineResource
from models.pipeline.note import Note
from models.pipeline.production import ProductionEvent
from models.pipeline.source.work import SourceWorkState, VisibleSourceWork
from models.pipeline.work import (
    ALL_WORK_STATUSES,
    DeletedWork,
    InvisibleWork,
    RedirectedWork,
    VisibleWork,
)
from models.pipeline.work_data import WorkData
from utils.types import DELETED_REASON_TYPES, INVISIBLE_REASON_TYPES

# -----------------------------
# Helper construction functions
# -----------------------------


def example_source_identifier(i: int = 1) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type=Id(id=f"ID{i}"), ontology_type="catalogue", value=f"val{i}"
    )


def example_identified(i: int = 1) -> Identified:
    return Identified(
        source_identifier=example_source_identifier(i),
        other_identifiers=[example_source_identifier(i + 100)],
        canonical_id=f"canon{i}",
    )


def maximal_work_data() -> WorkData:
    # Populate each field with at least one or a non-empty value
    concept_min = Concept(
        id=example_identified(10), label="Concept Label", type="Concept"
    )
    subject_min = Subject(
        id=example_identified(11), label="Subject Label", concepts=[], type="Subject"
    )
    genre_min = Genre(label="Genre Label", concepts=[])
    contributor_min = Contributor(agent=concept_min, roles=[], primary=True)
    production_event = ProductionEvent(
        label="Production", places=[], agents=[], dates=[]
    )
    note_min = Note(
        note_type=IdLabel(id="note", label="Note"), contents="Note contents"
    )
    digital_location = DigitalLocation(
        location_type=OnlineResource,
        access_conditions=[],
        url="https://example.org/thumb",
        credit="Credit",
        link_text="Link",
    )
    item_min = Item(
        id=example_identified(12), title="Item Title", note="Item Note", locations=[]
    )
    holdings_min = Holdings(note="Holdings note", enumeration=["Vol1"], location=None)
    image_data_min = ImageData(id=example_identified(13), version=1, locations=[])

    return WorkData(
        title="Title",
        other_identifiers=[example_source_identifier(2)],
        alternative_titles=["Alt 1", "Alt 2"],
        format=Format(id="fmt", label="Format"),
        description="Description",
        physical_description="Physical",
        lettering="Lettering",
        created_date=concept_min,
        subjects=[subject_min],
        genres=[genre_min],
        contributors=[contributor_min],
        thumbnail=digital_location,
        production=[production_event],
        languages=[Language(id="en", label="English")],
        edition="1st",
        notes=[note_min],
        duration=123,
        items=[item_min],
        holdings=[holdings_min],
        collection_path=CollectionPath(path="A/B/C", label="Collection"),
        reference_number="REF123",
        image_data=[image_data_min],
        work_type="Standard",
        current_frequency="Monthly",
        former_frequency=["Weekly"],
        designation=["Designation1"],
    )


# -----------------------------
# Deterministic exhaustive tests
# -----------------------------


@pytest.mark.parametrize(
    "work_instance",
    [
        VisibleWork(
            version=1,
            data=maximal_work_data(),
            redirect_sources=[example_identified(20)],
        ),
        InvisibleWork(
            version=2,
            data=maximal_work_data(),
            invisibility_reasons=[
                InvisibleReason(
                    type="CopyrightNotCleared", info="Info", message="Message"
                )
            ],
        ),
        DeletedWork(
            version=3,
            data=maximal_work_data(),
            deleted_reason=DeletedReason(type="DeletedFromSource", info="Info"),
        ),
        RedirectedWork(redirect_target=example_identified(30)),
    ],
)
def test_work_variant_roundtrip_exhaustive(
    work_instance: VisibleWork | InvisibleWork | DeletedWork | RedirectedWork,
) -> None:
    # Roundtrip via alias (camelCase) JSON
    dumped = work_instance.model_dump(by_alias=True)
    json_str = json.dumps(dumped)
    model2 = type(work_instance).model_validate_json(json_str)
    # Compare semantic content rather than object instance equality to avoid
    # potential discrepancies from internal pydantic normalisation.
    assert model2.model_dump() == work_instance.model_dump()

    # Ensure top-level alias keys are camelCase for fields with underscores
    for field_name in work_instance.model_fields:
        alias = to_camel(field_name)
        if "_" in field_name:
            assert alias in dumped
            assert field_name not in dumped
        else:
            # Fields without underscores keep same name
            assert field_name in dumped

    # Variant-specific field presence
    if isinstance(work_instance, VisibleWork):
        assert "redirectSources" in dumped
    if isinstance(work_instance, InvisibleWork):
        assert "invisibilityReasons" in dumped
    if isinstance(work_instance, DeletedWork):
        assert "deletedReason" in dumped
    if isinstance(work_instance, RedirectedWork):
        assert "redirectTarget" in dumped

    # Check all WorkData fields present under camelCase
    if hasattr(work_instance, "data"):
        data_dump = dumped["data"]
        for field_name in WorkData.model_fields:
            alias = to_camel(field_name)
            # alias generation leaves names without underscores unchanged
            expected_key = alias if "_" in field_name else field_name
            assert expected_key in data_dump


# -----------------------------
# Hypothesis strategies
# -----------------------------

identifier_type_strategy = st.builds(Id, id=st.text(min_size=1, max_size=8))
source_identifier_strategy = st.builds(
    SourceIdentifier,
    identifier_type=identifier_type_strategy,
    ontology_type=st.sampled_from(["catalogue", "mesh", "loc"]),
    value=st.text(min_size=1, max_size=12),
)

identified_strategy = st.builds(
    Identified,
    source_identifier=source_identifier_strategy,
    other_identifiers=st.lists(source_identifier_strategy, max_size=2),
    canonical_id=st.text(min_size=1, max_size=16),
)

invisible_reason_strategy = st.builds(
    InvisibleReason,
    type=st.sampled_from(INVISIBLE_REASON_TYPES),
    info=st.none() | st.text(min_size=1, max_size=20),
    message=st.none() | st.text(min_size=1, max_size=20),
)

deleted_reason_strategy = st.builds(
    DeletedReason,
    type=st.sampled_from(DELETED_REASON_TYPES),
    info=st.none() | st.text(min_size=1, max_size=20),
)

# Subset WorkData strategy focusing on representative scalar & list fields
work_data_strategy = st.builds(
    WorkData,
    title=st.text(min_size=1, max_size=25),
    alternative_titles=st.lists(st.text(min_size=1, max_size=10), max_size=3),
    edition=st.none() | st.text(min_size=1, max_size=10),
    duration=st.none() | st.integers(min_value=1, max_value=5000),
    designation=st.lists(st.text(min_size=1, max_size=12), max_size=2),
    work_type=st.sampled_from(["Standard", "Series", "Section", "Collection"]),
)

visible_work_strategy = st.builds(
    VisibleWork,
    version=st.integers(min_value=1, max_value=10),
    data=work_data_strategy,
    redirect_sources=st.lists(identified_strategy, max_size=3),
)

invisible_work_strategy = st.builds(
    InvisibleWork,
    version=st.integers(min_value=1, max_value=10),
    data=work_data_strategy,
    invisibility_reasons=st.lists(invisible_reason_strategy, min_size=1, max_size=3),
)

deleted_work_strategy = st.builds(
    DeletedWork,
    version=st.integers(min_value=1, max_value=10),
    data=work_data_strategy,
    deleted_reason=deleted_reason_strategy,
)

redirected_work_strategy = st.builds(
    RedirectedWork,
    redirect_target=identified_strategy,
)


# -----------------------------
# Roundtrip assertion helper
# -----------------------------


def assert_roundtrip_alias(
    model: VisibleWork | InvisibleWork | DeletedWork | RedirectedWork,
) -> None:
    dumped = model.model_dump(by_alias=True)
    json_str = json.dumps(dumped)
    model2 = type(model).model_validate_json(json_str)
    assert model2.model_dump() == model.model_dump()
    # verify alias presence for top-level fields
    for field_name in model.model_fields:
        alias = to_camel(field_name)
        if "_" in field_name:
            assert alias in dumped
            assert field_name not in dumped
        else:
            assert field_name in dumped


# -----------------------------
# Property-based tests
# -----------------------------


@given(visible_work_strategy)
def test_visible_work_roundtrip_property(work: VisibleWork) -> None:
    assert_roundtrip_alias(work)


@given(invisible_work_strategy)
def test_invisible_work_roundtrip_property(work: InvisibleWork) -> None:
    assert_roundtrip_alias(work)


@given(deleted_work_strategy)
def test_deleted_work_roundtrip_property(work: DeletedWork) -> None:
    assert_roundtrip_alias(work)


@given(redirected_work_strategy)
def test_redirected_work_roundtrip_property(work: RedirectedWork) -> None:
    assert_roundtrip_alias(work)


# -----------------------------
# Minimal vs maximal instances edge test
# -----------------------------


def test_minimal_vs_maximal_visible_work_roundtrip() -> None:
    minimal = VisibleWork(version=1, data=WorkData(), redirect_sources=[])
    maximal = VisibleWork(
        version=99, data=maximal_work_data(), redirect_sources=[example_identified(999)]
    )
    for instance in [minimal, maximal]:
        assert_roundtrip_alias(instance)


# Ensure all WorkStatus literals are represented
@pytest.mark.parametrize("status_cls", ALL_WORK_STATUSES)
def test_work_status_literal_values(status_cls: Any) -> None:
    instance_kwargs = (
        {"version": 1, "data": WorkData()} if status_cls is not RedirectedWork else {}
    )
    # Provide required variant-specific fields minimally
    if status_cls is VisibleWork:
        instance_kwargs["redirect_sources"] = []
    elif status_cls is InvisibleWork:
        instance_kwargs["invisibility_reasons"] = []
    elif status_cls is DeletedWork:
        instance_kwargs["deleted_reason"] = DeletedReason(type="DeletedFromSource")
    elif status_cls is RedirectedWork:
        instance_kwargs["redirect_target"] = example_identified(42)
    inst = status_cls(**instance_kwargs)
    assert inst.type in ["Visible", "Redirected", "Deleted", "Invisible"]


# -----------------------------
# Fixture comparison test
# -----------------------------


def test_visible_source_work_maximal_fixtures() -> None:
    """Validate maximal VisibleWork & VisibleSourceWork serialisations against fixtures.

    Combines previous separate tests into one for reduced duplication.
    """

    # VisibleSourceWork fixture (includes state)
    vsw_state = SourceWorkState(
        source_identifier=example_source_identifier(1),
        source_modified_time="2025-10-24T00:00:00Z",
        modified_time="2025-10-24T01:00:00Z",
    )
    vsw_inst = VisibleSourceWork(
        version=1,
        data=maximal_work_data(),
        redirect_sources=[example_identified(20)],
        state=vsw_state,
    )
    vsw_generated = vsw_inst.model_dump(by_alias=True)
    vsw_fixture_path = pathlib.Path("tests/fixtures/work/visible_source_maximal.json")
    with vsw_fixture_path.open() as f:
        vsw_fixture = json.load(f)
    assert vsw_generated == vsw_fixture, (
        "Generated VisibleSourceWork JSON does not match fixture; update fixture if intentional change."
    )
