"""Port of the harmonisation cases of SierraSubjectsTest.scala, SierraContributorsTest.scala and SubjectsAndContributorsTest.scala."""

import pytest

from adapters.transformers.marc.harmonisation import harmonise_types
from models.pipeline.concept import (
    Concept,
    Contributor,
    DateTimeRange,
    Period,
    Subject,
)
from models.pipeline.id_label import Id
from models.pipeline.identifier import Identifiable, SourceIdentifier
from utils.types import RawConceptType


def _identifiable(ontology_type: str, value: str = "sh00000000") -> Identifiable:
    return Identifiable.from_source_identifier(
        SourceIdentifier(
            identifier_type=Id(id="lc-subjects"),
            ontology_type=ontology_type,
            value=value,
        )
    )


def _subject(label: str, concept_type: RawConceptType) -> Subject:
    return Subject(
        label=label,
        id=_identifiable(concept_type),
        concepts=[
            Concept(label=label, id=_identifiable(concept_type), type=concept_type)
        ],
    )


def _contributor(label: str, agent_type: RawConceptType) -> Contributor:
    return Contributor(
        agent=Concept(label=label, id=_identifiable(agent_type), type=agent_type)
    )


def _ontology_type(concept: Concept) -> str:
    assert isinstance(concept.id, Identifiable)
    return concept.id.source_identifier.ontology_type


@pytest.mark.parametrize("specific_type", ["Person", "Organisation", "Place"])
def test_subjects_sharing_an_identifier_take_the_specific_type(
    specific_type: RawConceptType,
) -> None:
    subjects, _ = harmonise_types(
        [
            _subject("Maimonides", specific_type),
            _subject("Maimonides, in his work on Logic", "Concept"),
        ],
        [],
    )
    assert len(subjects) == 2
    for subject in subjects:
        assert _ontology_type(subject) == specific_type
        assert subject.concepts[0].type == specific_type
        assert _ontology_type(subject.concepts[0]) == specific_type


def test_collapses_subjects_that_are_no_longer_unique() -> None:
    subjects, _ = harmonise_types(
        [
            _subject("Maimonides", "Person"),
            _subject("Maimonides", "Organisation"),
            _subject("Maimonides", "Concept"),
        ],
        [],
    )
    assert len(subjects) == 1
    assert _ontology_type(subjects[0]) == "Person"


def test_chooses_between_specific_types_by_priority() -> None:
    """Scala takes the first specific type it meets; the priority order of the catalogue graph decides here."""
    subjects, _ = harmonise_types(
        [
            _subject("Maimonides and his chums", "Organisation"),
            _subject("Maimonides", "Person"),
            _subject("Maimonides, in his work on Logic", "Concept"),
        ],
        [],
    )
    assert [_ontology_type(subject) for subject in subjects] == ["Person"] * 3


@pytest.mark.parametrize("specific_type", ["Person", "Organisation", "Meeting"])
def test_contributors_sharing_an_identifier_take_the_specific_type(
    specific_type: RawConceptType,
) -> None:
    _, contributors = harmonise_types(
        [],
        [
            _contributor("Maimonides, in his work on Logic", "Agent"),
            _contributor("Maimonides", specific_type),
        ],
    )
    assert len(contributors) == 2
    for contributor in contributors:
        assert contributor.agent.type == specific_type
        assert _ontology_type(contributor.agent) == specific_type


def test_collapses_contributors_that_are_no_longer_unique() -> None:
    _, contributors = harmonise_types(
        [],
        [
            _contributor("Maimonides", "Agent"),
            _contributor("Maimonides", "Person"),
            _contributor("Maimonides", "Agent"),
        ],
    )
    assert len(contributors) == 1
    assert contributors[0].agent.type == "Person"


def test_contributor_takes_the_type_of_a_matching_subject() -> None:
    glasgow_as_a_place = _subject("Glasgow", "Place")
    subjects, contributors = harmonise_types(
        [glasgow_as_a_place], [_contributor("Glasgow", "Organisation")]
    )
    assert subjects == [glasgow_as_a_place]
    assert contributors[0].agent.type == "Place"
    assert _ontology_type(contributors[0].agent) == "Place"


def test_subject_takes_the_type_of_a_matching_contributor() -> None:
    """Scala only ever retypes the contributor; here the rule applies both ways."""
    subjects, contributors = harmonise_types(
        [_subject("Gall, F. J.", "Concept")], [_contributor("Gall, F. J.", "Person")]
    )
    assert _ontology_type(subjects[0]) == "Person"
    assert subjects[0].concepts[0].type == "Person"
    assert contributors[0].agent.type == "Person"


def test_concepts_with_different_identifiers_are_left_alone() -> None:
    subjects = [_subject("Glasgow", "Place")]
    contributors = [
        Contributor(
            agent=Concept(
                label="Glasgow",
                id=_identifiable("Organisation", value="n00000000"),
                type="Organisation",
            )
        )
    ]
    assert harmonise_types(subjects, contributors) == (subjects, contributors)


def test_a_period_keeps_its_range() -> None:
    """Scala rebuilt every harmonised concept from its type, identifier and label, so a Period lost its range."""
    period = Period(
        label="19th century",
        id=_identifiable("Period"),
        range=DateTimeRange.model_validate(
            {"from": "1800-01-01T00:00:00Z", "to": "1899-12-31T23:59:59Z"}
        ),
    )
    subjects, _ = harmonise_types(
        [
            Subject(
                label="19th century", id=_identifiable("Period"), concepts=[period]
            ),
            _subject("1800s", "Concept"),
        ],
        [],
    )
    assert [_ontology_type(subject) for subject in subjects] == ["Period", "Period"]
    assert subjects[0].concepts[0] == period
