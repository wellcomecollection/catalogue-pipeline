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


def test_lowest_id_wins_across_the_same_source_group() -> None:
    # Caricature (D019492) and Cartoon (D019493) are both MeSH; the lower id wins
    result = _transform(load_json_fixture(SIBLINGS_FIXTURE))

    assert result.query.label == "Caricature"
    assert result.display.displayLabel == "Caricature"
    assert result.query.identifiers == [
        ConceptIdentifier(value="sh85020238", identifierType="lc-subjects"),
        ConceptIdentifier(value="D019492", identifierType="nlm-mesh"),
    ]


def test_a_lower_id_sibling_beats_the_linked_source_concept() -> None:
    # The concept links to Caricature (D019492), but Cartoon joins the group with a lower id
    mock_concept = load_json_fixture(SIBLINGS_FIXTURE)
    _source_concept(mock_concept, "D019493")["~properties"]["id"] = "D000001"

    assert _transform(mock_concept).display.displayLabel == "Cartoon"


def test_same_as_group_members_share_a_label() -> None:
    # Two members of one group linked to different MeSH nodes must agree on the label,
    # since the site's "View all" links filter works by that label
    caricature = load_json_fixture(SIBLINGS_FIXTURE)
    cartoon = {
        **caricature,
        "concept": {
            **caricature["concept"],
            "~id": "e5drba69",
            "~properties": {**caricature["concept"]["~properties"], "id": "e5drba69"},
        },
        "linked_source_concepts": [_source_concept(caricature, "D019493")],
        "same_as": ["hv3ueb5k"],
    }

    caricature_result = _transform(caricature)
    cartoon_result = _transform(cartoon)

    assert caricature_result.display.displayLabel == "Caricature"
    assert cartoon_result.display.displayLabel == caricature_result.display.displayLabel
    assert cartoon_result.query.label == caricature_result.query.label
    assert cartoon_result.query.identifiers == [
        ConceptIdentifier(value="D019493", identifierType="nlm-mesh")
    ]


def test_a_blank_weco_authority_label_falls_through() -> None:
    result = _transform(load_json_fixture(SIBLINGS_FIXTURE))

    assert result.query.label == "Caricature"
    assert result.display.displayLabel == "Caricature"
    assert "" not in result.query.alternativeLabels


def test_every_other_source_heading_is_an_alternative_label() -> None:
    # The MeSH sibling, the LCSH heading and both Wikidata labels stay searchable; the chosen label does not repeat
    result = _transform(load_json_fixture(SIBLINGS_FIXTURE))

    assert result.query.label == "Caricature"
    assert result.query.alternativeLabels == [
        "Caricatures",
        "Caricatures and cartoons",
        "Cartoon",
        "Cartoons",
        "Wikidata label",
        "Wikidata label without description",
    ]
    assert result.display.alternativeLabels == result.query.alternativeLabels


def test_a_source_concept_without_a_description_does_not_blank_another() -> None:
    result = _transform(load_json_fixture(SIBLINGS_FIXTURE))

    assert result.display.description == ConceptDescription(
        text="Wikidata description",
        sourceLabel="wikidata",
        sourceUrl="https://www.wikidata.org/wiki/Q2",
    )


def test_weco_authority_wins_query_and_display_and_keeps_the_displaced_headings() -> (
    None
):
    mock_concept = load_json_fixture(SIBLINGS_FIXTURE)
    _source_concept(mock_concept, "weco0001")["~properties"]["label"] = "Cartoons"

    result = _transform(mock_concept)

    assert result.query.label == "Cartoons"
    assert result.display.displayLabel == "Cartoons"
    assert result.query.alternativeLabels == [
        "Caricature",
        "Caricatures",
        "Caricatures and cartoons",
        "Cartoon",
        "Wikidata label",
        "Wikidata label without description",
    ]
    assert result.display.alternativeLabels == result.query.alternativeLabels


def test_query_and_display_labels_are_not_alternative_labels() -> None:
    # LC Names outranks Wikidata for querying but not for display, so the two labels differ and both must be dropped
    mock_concept = load_json_fixture(SIBLINGS_FIXTURE)
    for source_id in ["sh85020238", "D019492", "D019493"]:
        _source_concept(mock_concept, source_id)["~properties"]["source"] = "lc-names"

    result = _transform(mock_concept)

    assert result.query.label == "Caricature"
    assert result.display.displayLabel == "Wikidata label without description"
    assert "Caricature" not in result.query.alternativeLabels
    assert "Wikidata label without description" not in result.query.alternativeLabels
    assert result.display.alternativeLabels == result.query.alternativeLabels


@pytest.mark.parametrize("higher_id_is_linked", [False, True])
def test_lowest_id_weco_authority_wins_whether_or_not_another_is_linked(
    higher_id_is_linked: bool,
) -> None:
    mock_concept = load_json_fixture(WECO_FIXTURE)
    if higher_id_is_linked:
        mock_concept["linked_source_concepts"].append(
            _source_concept(mock_concept, "qwertyu34")
        )
    mock_concept["source_concepts"].append(
        _a_source_concept("aaaaaaaa", "weco-authority", "Another Wellcome Label")
    )

    result = _transform(mock_concept)

    assert result.query.label == "Another Wellcome Label"
    assert result.display.displayLabel == "Another Wellcome Label"
    assert "Wellcome Label" in result.query.alternativeLabels
    assert "Another Wellcome Label" not in result.query.alternativeLabels
