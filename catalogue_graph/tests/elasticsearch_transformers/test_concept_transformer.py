import io

import fixtures.neptune.neptune_generators as ng
from test_utils import load_json_fixture

from ingestor.models.concept import (
    RawNeptuneConcept,
    get_most_specific_concept_type,
)
from ingestor.models.indexable import DisplayIdentifier, DisplayIdentifierType
from ingestor.models.indexable_concept import (
    ConceptDescription,
    ConceptDisplay,
    ConceptIdentifier,
    ConceptQuery,
    ConceptRelatedTo,
    IndexableConcept,
    RelatedConcepts,
)
from ingestor.models.related_concepts import RawNeptuneRelatedConcepts
from ingestor.transformers.concepts_transformer import (
    ElasticsearchConceptsTransformer,
)

MOCK_EMPTY_RELATED_CONCEPTS: dict = {
    "related_to": {},
    "fields_of_work": {},
    "narrower_than": {},
    "broader_than": {},
    "people": {},
    "frequent_collaborators": {},
    "related_topics": {},
}


def test_catalogue_concept_from_neptune_result() -> None:
    mock_concept = load_json_fixture(
        "neptune/concept_query_single_alternative_labels.json"
    )

    alternative_labels = [
        "Alternative label",
        "Another alternative label",
        "MeSH alternative label",
    ]

    expected_result = IndexableConcept(
        query=ConceptQuery(
            id="id",
            identifiers=[ConceptIdentifier(value="123", identifierType="lc-names")],
            label="LoC label",
            alternativeLabels=alternative_labels,
            type="Person",
        ),
        display=ConceptDisplay(
            id="id",
            identifiers=[
                DisplayIdentifier(
                    value="123",
                    identifierType=DisplayIdentifierType(
                        id="lc-names",
                        label="Library of Congress Name authority records",
                        type="IdentifierType",
                    ),
                )
            ],
            label="LoC label",
            displayLabel="Wikidata label",
            alternativeLabels=alternative_labels,
            description=ConceptDescription(
                text="Description",
                sourceLabel="wikidata",
                sourceUrl="https://www.wikidata.org/wiki/456",
            ),
            type="Person",
            sameAs=[],
            relatedConcepts=RelatedConcepts(
                relatedTo=[],
                fieldsOfWork=[],
                narrowerThan=[],
                broaderThan=[],
                people=[],
                frequentCollaborators=[],
                relatedTopics=[],
            ),
        ),
    )

    transformer = ElasticsearchConceptsTransformer()
    neptune_concept = RawNeptuneConcept(mock_concept, MOCK_EMPTY_RELATED_CONCEPTS)
    neptune_related = RawNeptuneRelatedConcepts(
        neptune_concept.wellcome_id, MOCK_EMPTY_RELATED_CONCEPTS
    )
    result = transformer.transform_document(neptune_concept, neptune_related)
    assert result == expected_result


def test_catalogue_concept_from_neptune_result_without_alternative_labels() -> None:
    mock_concept = load_json_fixture("neptune/concept_query_single.json")

    expected_result = IndexableConcept(
        query=ConceptQuery(
            id="id",
            identifiers=[ConceptIdentifier(value="123", identifierType="lc-names")],
            label="LoC label",
            alternativeLabels=[],
            type="Person",
        ),
        display=ConceptDisplay(
            id="id",
            identifiers=[
                DisplayIdentifier(
                    value="123",
                    identifierType=DisplayIdentifierType(
                        id="lc-names",
                        label="Library of Congress Name authority records",
                        type="IdentifierType",
                    ),
                )
            ],
            label="LoC label",
            displayLabel="Wikidata label",
            alternativeLabels=[],
            description=ConceptDescription(
                text="Description",
                sourceLabel="wikidata",
                sourceUrl="https://www.wikidata.org/wiki/456",
            ),
            type="Person",
            sameAs=[],
            relatedConcepts=RelatedConcepts(
                relatedTo=[],
                fieldsOfWork=[],
                narrowerThan=[],
                broaderThan=[],
                people=[],
                frequentCollaborators=[],
                relatedTopics=[],
            ),
        ),
    )

    transformer = ElasticsearchConceptsTransformer()
    neptune_concept = RawNeptuneConcept(mock_concept, MOCK_EMPTY_RELATED_CONCEPTS)
    neptune_related = RawNeptuneRelatedConcepts(
        neptune_concept.wellcome_id, MOCK_EMPTY_RELATED_CONCEPTS
    )
    result = transformer.transform_document(neptune_concept, neptune_related)
    assert result == expected_result


def test_catalogue_concept_from_neptune_result_with_related_concepts() -> None:
    mock_concept = load_json_fixture("neptune/concept_query_single_waves.json")
    mock_related_to = load_json_fixture("neptune/related_to_query_single.json")[
        "related"
    ]

    related_concepts = MOCK_EMPTY_RELATED_CONCEPTS | {
        "related_to": {"a2584ttj": mock_related_to},
    }

    expected_result = IndexableConcept(
        query=ConceptQuery(
            id="a2584ttj",
            identifiers=[
                ConceptIdentifier(value="sh85145789", identifierType="lc-subjects")
            ],
            label="Waves",
            alternativeLabels=["Mechanical waves", "Waves"],
            type="Concept",
        ),
        display=ConceptDisplay(
            id="a2584ttj",
            identifiers=[
                DisplayIdentifier(
                    value="sh85145789",
                    identifierType=DisplayIdentifierType(
                        id="lc-subjects",
                        label="Library of Congress Subject Headings (LCSH)",
                        type="IdentifierType",
                    ),
                )
            ],
            label="Waves",
            displayLabel="Waves",
            alternativeLabels=["Mechanical waves", "Waves"],
            description=ConceptDescription(
                text="Repeated oscillation about a stable equilibrium",
                sourceLabel="wikidata",
                sourceUrl="https://www.wikidata.org/wiki/Q37172",
            ),
            type="Concept",
            sameAs=["a2584ttj", "gcmn66yk"],
            relatedConcepts=RelatedConcepts(
                relatedTo=[
                    ConceptRelatedTo(
                        label="Hilton, Violet, 1908-1969",
                        id="tzrtx26u",
                        relationshipType="has_sibling",
                        conceptType="Person",
                    )
                ],
                fieldsOfWork=[],
                narrowerThan=[],
                broaderThan=[],
                people=[],
                frequentCollaborators=[],
                relatedTopics=[],
            ),
        ),
    )

    transformer = ElasticsearchConceptsTransformer()
    neptune_concept = RawNeptuneConcept(mock_concept, related_concepts)
    neptune_related = RawNeptuneRelatedConcepts(
        neptune_concept.wellcome_id, related_concepts
    )
    result = transformer.transform_document(neptune_concept, neptune_related)
    assert result == expected_result


def test_catalogue_concept_from_neptune_result_with_multiple_related_concepts() -> None:
    mock_concept = load_json_fixture("neptune/concept_query_single_waves.json")
    import fixtures.neptune.neptune_generators as ng

    mock_related_to = [
        ng.a_related_concept(),
        ng.a_related_concept_with_two_source_nodes()
    ]

    related_concepts = MOCK_EMPTY_RELATED_CONCEPTS | {
        "related_to": {"a2584ttj": mock_related_to},
    }

    expected_result = IndexableConcept(
        query=ConceptQuery(
            id="a2584ttj",
            identifiers=[
                ConceptIdentifier(value="sh85145789", identifierType="lc-subjects")
            ],
            label="Waves",
            alternativeLabels=["Mechanical waves", "Waves"],
            type="Concept",
        ),
        display=ConceptDisplay(
            id="a2584ttj",
            identifiers=[
                DisplayIdentifier(
                    value="sh85145789",
                    identifierType=DisplayIdentifierType(
                        id="lc-subjects",
                        label="Library of Congress Subject Headings (LCSH)",
                        type="IdentifierType",
                    ),
                )
            ],
            label="Waves",
            displayLabel="Waves",
            alternativeLabels=["Mechanical waves", "Waves"],
            description=ConceptDescription(
                text="Repeated oscillation about a stable equilibrium",
                sourceLabel="wikidata",
                sourceUrl="https://www.wikidata.org/wiki/Q37172",
            ),
            type="Concept",
            sameAs=["a2584ttj", "gcmn66yk"],
            relatedConcepts=RelatedConcepts(
                relatedTo=[
                    ConceptRelatedTo(
                        label="Hilton, Violet, 1908-1969",
                        id="tzrtx26u",
                        relationshipType="has_sibling",
                        conceptType="Person",
                    ),
                    ConceptRelatedTo(
                        label="Joseph Pujol",
                        id="abcd2345",
                        relationshipType="has_sibling",
                        conceptType="Person",
                    )
                ],
                fieldsOfWork=[],
                narrowerThan=[],
                broaderThan=[],
                people=[],
                frequentCollaborators=[],
                relatedTopics=[],
            ),
        ),
    )

    transformer = ElasticsearchConceptsTransformer()
    neptune_concept = RawNeptuneConcept(mock_concept, related_concepts)
    neptune_related = RawNeptuneRelatedConcepts(
        neptune_concept.wellcome_id, related_concepts
    )
    result = transformer.transform_document(neptune_concept, neptune_related)
    assert result == expected_result


def test_catalogue_concept_ignore_unlabelled_related_concepts() -> None:
    mock_concept = load_json_fixture("neptune/concept_query_single_waves.json")

    mock_related_to = [
        ng.a_related_concept_with_no_label(),
        ng.a_related_concept(),
        ng.a_related_concept_with_no_label(),
        ng.a_related_concept_with_two_source_nodes()
    ]

    related_concepts = MOCK_EMPTY_RELATED_CONCEPTS | {
        "related_to": {"a2584ttj": mock_related_to},
    }

    expected_result = IndexableConcept(
        query=ConceptQuery(
            id="a2584ttj",
            identifiers=[
                ConceptIdentifier(value="sh85145789", identifierType="lc-subjects")
            ],
            label="Waves",
            alternativeLabels=["Mechanical waves", "Waves"],
            type="Concept",
        ),
        display=ConceptDisplay(
            id="a2584ttj",
            identifiers=[
                DisplayIdentifier(
                    value="sh85145789",
                    identifierType=DisplayIdentifierType(
                        id="lc-subjects",
                        label="Library of Congress Subject Headings (LCSH)",
                        type="IdentifierType",
                    ),
                )
            ],
            label="Waves",
            displayLabel="Waves",
            alternativeLabels=["Mechanical waves", "Waves"],
            description=ConceptDescription(
                text="Repeated oscillation about a stable equilibrium",
                sourceLabel="wikidata",
                sourceUrl="https://www.wikidata.org/wiki/Q37172",
            ),
            type="Concept",
            sameAs=["a2584ttj", "gcmn66yk"],
            relatedConcepts=RelatedConcepts(
                relatedTo=[
                    ConceptRelatedTo(
                        label="Hilton, Violet, 1908-1969",
                        id="tzrtx26u",
                        relationshipType="has_sibling",
                        conceptType="Person",
                    ),
                    ConceptRelatedTo(
                        label="Joseph Pujol",
                        id="abcd2345",
                        relationshipType="has_sibling",
                        conceptType="Person",
                    )
                ],
                fieldsOfWork=[],
                narrowerThan=[],
                broaderThan=[],
                people=[],
                frequentCollaborators=[],
                relatedTopics=[],
            ),
        ),
    )

    transformer = ElasticsearchConceptsTransformer()
    neptune_concept = RawNeptuneConcept(mock_concept, related_concepts)
    neptune_related = RawNeptuneRelatedConcepts(
        neptune_concept.wellcome_id, related_concepts
    )
    result = transformer.transform_document(neptune_concept, neptune_related)
    assert result == expected_result


def test_catalogue_concept_overridden_related_concepts() -> None:
    mock_concept = load_json_fixture("neptune/concept_query_single_waves.json")

    mock_related_to = [
        ng.a_related_concept_with_no_label(),
        ng.a_related_concept(),
        ng.a_related_concept_with_two_source_nodes()
    ]

    related_concepts = MOCK_EMPTY_RELATED_CONCEPTS | {
        "related_to": {"a2584ttj": mock_related_to},
    }

    expected_result = IndexableConcept(
        query=ConceptQuery(
            id="a2584ttj",
            identifiers=[
                ConceptIdentifier(value="sh85145789", identifierType="lc-subjects")
            ],
            label="Waves",
            alternativeLabels=["Mechanical waves", "Waves"],
            type="Concept",
        ),
        display=ConceptDisplay(
            id="a2584ttj",
            identifiers=[
                DisplayIdentifier(
                    value="sh85145789",
                    identifierType=DisplayIdentifierType(
                        id="lc-subjects",
                        label="Library of Congress Subject Headings (LCSH)",
                        type="IdentifierType",
                    ),
                )
            ],
            label="Waves",
            displayLabel="Waves",
            alternativeLabels=["Mechanical waves", "Waves"],
            description=ConceptDescription(
                text="Repeated oscillation about a stable equilibrium",
                sourceLabel="wikidata",
                sourceUrl="https://www.wikidata.org/wiki/Q37172",
            ),
            type="Concept",
            sameAs=["a2584ttj", "gcmn66yk"],
            relatedConcepts=RelatedConcepts(
                relatedTo=[
                    ConceptRelatedTo(
                        label="Roland le Petour",
                        id="aaaaaaaa",
                        relationshipType="has_sibling",
                        conceptType="Person",
                    ),
                    ConceptRelatedTo(
                        label="Hilton, Violet, 1908-1969",
                        id="tzrtx26u",
                        relationshipType="has_sibling",
                        conceptType="Person",
                    ),
                    ConceptRelatedTo(
                        label="Le Pétomane",
                        id="abcd2345",
                        relationshipType="has_sibling",
                        conceptType="Person",
                    )
                ],
                fieldsOfWork=[],
                narrowerThan=[],
                broaderThan=[],
                people=[],
                frequentCollaborators=[],
                relatedTopics=[],
            ),
        ),
    )

    transformer = ElasticsearchConceptsTransformer(
        io.StringIO("""id,label,description
        id, Wellcome Label, Wellcome Description
        aaaaaaaa,Roland le Petour,
        abcd2345,Le Pétomane,
        """)

    )
    neptune_concept = RawNeptuneConcept(mock_concept, related_concepts)
    neptune_related = RawNeptuneRelatedConcepts(
        neptune_concept.wellcome_id, related_concepts
    )
    result = transformer.transform_document(neptune_concept, neptune_related)
    assert result == expected_result


def test_concept_type_agent_precedence() -> None:
    # Person is more specific than Agent
    assert get_most_specific_concept_type(["Agent", "Person"]) == "Person"

    # Ordering does not matter
    assert get_most_specific_concept_type(["Person", "Agent"]) == "Person"

    # Same for Organisation and Agent
    assert get_most_specific_concept_type(["Agent", "Organisation"]) == "Organisation"
    assert get_most_specific_concept_type(["Organisation", "Agent"]) == "Organisation"

    # Person/Agent/Organisation take precedence over general Concept/Subject types
    assert get_most_specific_concept_type(["Person", "Concept", "Subject"]) == "Person"
    assert (
            get_most_specific_concept_type(["Concept", "Organisation", "Subject"])
            == "Organisation"
    )
    assert get_most_specific_concept_type(["Concept", "Subject", "Agent"]) == "Agent"


def test_concept_type_genre_precedence() -> None:
    # Genre has precedence over everything else. The presence of the 'Genre' type determines whether the
    # "Using this Type/Technique" tab shows in the frontend, so we err on the side of showing it on pages where
    # it shouldn't be shown rather than hiding it on pages where it should be.
    assert get_most_specific_concept_type(["Concept", "Subject", "Genre"]) == "Genre"
    assert get_most_specific_concept_type(["Agent", "Genre", "Person"]) == "Genre"
    assert get_most_specific_concept_type(["Genre", "Place"]) == "Genre"
    assert (
            get_most_specific_concept_type(
                [
                    "Genre",
                    "Place",
                    "Person",
                    "Organisation",
                    "Period",
                    "Meeting",
                    "Agent",
                    "Subject",
                    "Concept",
                ]
            )
            == "Genre"
    )


def test_concept_type_place_precedence() -> None:
    # Place has precedence over everything (except for Genre).
    assert (
            get_most_specific_concept_type(
                [
                    "Place",
                    "Person",
                    "Organisation",
                    "Period",
                    "Meeting",
                    "Agent",
                    "Subject",
                    "Concept",
                ]
            )
            == "Place"
    )

    assert get_most_specific_concept_type(["Concept", "Subject", "Place"]) == "Place"

    # Place/Organisation and Place/Person combinations are quite common (even though they are mutually exclusive).
    # We should always pick Place as it's usually the correct one.
    assert get_most_specific_concept_type(["Place", "Person"]) == "Place"
    assert get_most_specific_concept_type(["Place", "Organisation"]) == "Place"
    assert (
            get_most_specific_concept_type(["Agent", "Place", "Person", "Organisation"])
            == "Place"
    )


def test_catalogue_concept_from_neptune_result_with_overridden_label_and_description() -> None:
    mock_concept = load_json_fixture("neptune/concept_query_single.json")

    expected_result = IndexableConcept(
        query=ConceptQuery(
            id="id",
            identifiers=[ConceptIdentifier(value="123", identifierType="lc-names")],
            label="LoC label",
            alternativeLabels=[],
            type="Person",
        ),
        display=ConceptDisplay(
            id="id",
            identifiers=[
                DisplayIdentifier(
                    value="123",
                    identifierType=DisplayIdentifierType(
                        id="lc-names",
                        label="Library of Congress Name authority records",
                        type="IdentifierType",
                    ),
                )
            ],
            label="LoC label",
            displayLabel="Wellcome Label",
            alternativeLabels=[],
            description=ConceptDescription(
                text="Wellcome Description",
                sourceLabel="wellcome",
                sourceUrl="",
            ),
            type="Person",
            sameAs=[],
            relatedConcepts=RelatedConcepts(
                relatedTo=[],
                fieldsOfWork=[],
                narrowerThan=[],
                broaderThan=[],
                people=[],
                frequentCollaborators=[],
                relatedTopics=[],
            ),
        ),
    )
    transformer = ElasticsearchConceptsTransformer(
        io.StringIO("""id,label,description
        id, Wellcome Label, Wellcome Description
        """)
    )
    neptune_concept = RawNeptuneConcept(mock_concept, MOCK_EMPTY_RELATED_CONCEPTS)
    neptune_related = RawNeptuneRelatedConcepts(
        neptune_concept.wellcome_id, MOCK_EMPTY_RELATED_CONCEPTS
    )
    result = transformer.transform_document(neptune_concept, neptune_related)
    assert result == expected_result
