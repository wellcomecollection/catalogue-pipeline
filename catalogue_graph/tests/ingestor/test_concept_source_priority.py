import itertools

import pytest

from ingestor.models.indexable.concept import (
    ConceptDescription,
    ConceptIdentifier,
    IndexableConcept,
)
from ingestor.models.neptune.query_result import ExtractedConcept
from tests.ingestor.test_concept_transformer import (
    MOCK_EMPTY_RELATED_CONCEPTS,
    get_transformer,
)
from tests.test_utils import load_json_fixture

WECO_FIXTURE = "ingestor/extractor/concept_with_weco_authority.json"
SIBLINGS_FIXTURE = "ingestor/extractor/concept_with_same_source_siblings.json"


def _transform(mock_concept: dict) -> IndexableConcept:
    raw_data = (ExtractedConcept(**mock_concept), MOCK_EMPTY_RELATED_CONCEPTS)
    result = get_transformer().transform_document(raw_data)
    assert result is not None
    return result


def _a_source_concept(source_concept_id: str, source: str, label: str) -> dict:
    return {
        "~id": source_concept_id,
        "~labels": ["SourceConcept"],
        "~properties": {"id": source_concept_id, "source": source, "label": label},
    }


def _source_concept(mock_concept: dict, source_concept_id: str) -> dict:
    return next(
        sc for sc in mock_concept["source_concepts"] if sc["~id"] == source_concept_id
    )


@pytest.mark.parametrize("fixture", [WECO_FIXTURE, SIBLINGS_FIXTURE])
def test_concept_does_not_depend_on_source_concept_order(fixture: str) -> None:
    mock_concept = load_json_fixture(fixture)
    expected = _transform(mock_concept)

    for source_concepts in itertools.permutations(mock_concept["source_concepts"]):
        for linked in itertools.permutations(mock_concept["linked_source_concepts"]):
            shuffled = {
                **mock_concept,
                "source_concepts": list(source_concepts),
                "linked_source_concepts": list(linked),
            }
            assert _transform(shuffled) == expected


def test_linked_source_concept_beats_a_same_source_sibling() -> None:
    # The concept links to Caricature (D019492); Cartoon (D019493) only joins via the same-as group
    result = _transform(load_json_fixture(SIBLINGS_FIXTURE))

    assert result.query.label == "Caricature"
    assert result.display.displayLabel == "Caricature"
    assert result.query.identifiers == [
        ConceptIdentifier(value="sh85020238", identifierType="lc-subjects"),
        ConceptIdentifier(value="D019492", identifierType="nlm-mesh"),
    ]


def test_linked_source_concept_beats_a_lower_id_sibling() -> None:
    mock_concept = load_json_fixture(SIBLINGS_FIXTURE)
    _source_concept(mock_concept, "D019493")["~properties"]["id"] = "D000001"

    assert _transform(mock_concept).display.displayLabel == "Caricature"


def test_a_blank_weco_authority_label_falls_through() -> None:
    result = _transform(load_json_fixture(SIBLINGS_FIXTURE))

    assert result.query.label == "Caricature"
    assert result.display.displayLabel == "Caricature"
    assert result.query.alternativeLabels == ["Caricatures", "Cartoons"]


def test_a_source_concept_without_a_description_does_not_blank_another() -> None:
    result = _transform(load_json_fixture(SIBLINGS_FIXTURE))

    assert result.display.description == ConceptDescription(
        text="Wikidata description",
        sourceLabel="wikidata",
        sourceUrl="https://www.wikidata.org/wiki/Q2",
    )


def test_weco_authority_wins_query_and_display_and_keeps_the_displaced_heading() -> (
    None
):
    mock_concept = load_json_fixture(SIBLINGS_FIXTURE)
    _source_concept(mock_concept, "weco0001")["~properties"]["label"] = "Cartoons"

    result = _transform(mock_concept)

    assert result.query.label == "Cartoons"
    assert result.display.displayLabel == "Cartoons"
    assert result.query.alternativeLabels == ["Caricature", "Caricatures", "Cartoons"]
    assert result.display.alternativeLabels == result.query.alternativeLabels


def test_linked_weco_authority_beats_a_lower_id_weco_authority_sibling() -> None:
    mock_concept = load_json_fixture(WECO_FIXTURE)
    mock_concept["linked_source_concepts"].append(
        _source_concept(mock_concept, "qwertyu34")
    )
    mock_concept["source_concepts"].append(
        _a_source_concept("aaaaaaaa", "weco-authority", "Another Wellcome Label")
    )

    result = _transform(mock_concept)

    assert result.query.label == "Wellcome Label"
    assert result.display.displayLabel == "Wellcome Label"


def test_lowest_id_breaks_a_tie_between_unlinked_weco_authority_concepts() -> None:
    mock_concept = load_json_fixture(WECO_FIXTURE)
    mock_concept["source_concepts"].append(
        _a_source_concept("aaaaaaaa", "weco-authority", "Another Wellcome Label")
    )

    result = _transform(mock_concept)

    assert result.display.displayLabel == "Another Wellcome Label"
    assert "Wellcome Label" not in result.query.alternativeLabels
