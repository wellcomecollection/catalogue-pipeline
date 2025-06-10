from models.catalogue_concept import (
    CatalogueConcept,
    CatalogueConceptIdentifier,
    CatalogueConceptRelatedTo,
    ConceptDescription,
    ConceptsQuerySingleResult,
    RelatedConcepts,
    get_most_specific_concept_type,
)
from test_utils import load_json_fixture


def test_catalogue_concept_from_neptune_result() -> None:
    mock_concept = load_json_fixture(
        "neptune/concept_query_single_alternative_labels.json"
    )

    neptune_result = ConceptsQuerySingleResult(
        concept=mock_concept,
        related_to=[],
        fields_of_work=[],
        narrower_than=[],
        broader_than=[],
        people=[],
        referenced_together=[],
        frequent_collaborators=[],
        related_topics=[],
    )

    assert CatalogueConcept.from_neptune_result(neptune_result) == CatalogueConcept(
        id="id",
        identifiers=[
            CatalogueConceptIdentifier(value="456", identifierType="lc-names")
        ],
        label="label",
        alternativeLabels=[
            "Alternative label",
            "Another alternative label",
            "MeSH alternative label",
        ],
        description=ConceptDescription(
            text="Mesh description",
            sourceLabel="nlm-mesh",
            sourceUrl="https://meshb.nlm.nih.gov/record/ui?ui=789",
        ),
        type="Person",
        sameAs=[],
        relatedConcepts=RelatedConcepts(
            relatedTo=[],
            fieldsOfWork=[],
            narrowerThan=[],
            broaderThan=[],
            people=[],
            referencedTogether=[],
            frequentCollaborators=[],
            relatedTopics=[],
        ),
    )


def test_catalogue_concept_from_neptune_result_without_alternative_labels() -> None:
    mock_concept = load_json_fixture("neptune/concept_query_single.json")

    neptune_result = ConceptsQuerySingleResult(
        concept=mock_concept,
        related_to=[],
        fields_of_work=[],
        narrower_than=[],
        broader_than=[],
        people=[],
        referenced_together=[],
        frequent_collaborators=[],
        related_topics=[],
    )

    assert CatalogueConcept.from_neptune_result(neptune_result) == CatalogueConcept(
        id="id",
        identifiers=[
            CatalogueConceptIdentifier(value="456", identifierType="lc-names")
        ],
        label="label",
        alternativeLabels=[],
        description=ConceptDescription(
            text="Mesh description",
            sourceLabel="nlm-mesh",
            sourceUrl="https://meshb.nlm.nih.gov/record/ui?ui=789",
        ),
        type="Person",
        sameAs=[],
        relatedConcepts=RelatedConcepts(
            relatedTo=[],
            fieldsOfWork=[],
            narrowerThan=[],
            broaderThan=[],
            people=[],
            referencedTogether=[],
            frequentCollaborators=[],
            relatedTopics=[],
        ),
    )


def test_catalogue_concept_from_neptune_result_with_related_concepts() -> None:
    mock_concept = load_json_fixture("neptune/concept_query_single_waves.json")
    mock_related_to = load_json_fixture("neptune/related_to_query_single.json")[
        "related"
    ]

    neptune_result = ConceptsQuerySingleResult(
        concept=mock_concept,
        related_to=mock_related_to,
        fields_of_work=[],
        narrower_than=[],
        broader_than=[],
        people=[],
        referenced_together=[],
        frequent_collaborators=[],
        related_topics=[],
    )

    assert CatalogueConcept.from_neptune_result(neptune_result) == CatalogueConcept(
        id="a2584ttj",
        identifiers=[
            CatalogueConceptIdentifier(value="sh85145789", identifierType="lc-subjects")
        ],
        label="Waves",
        alternativeLabels=["Mechanical waves", "Waves"],
        description=ConceptDescription(
            text="Repeated oscillation about a stable equilibrium",
            sourceLabel="wikidata",
            sourceUrl="https://www.wikidata.org/wiki/Q37172",
        ),
        type="Concept",
        sameAs=["gcmn66yk", "a2584ttj"],
        relatedConcepts=RelatedConcepts(
            relatedTo=[
                CatalogueConceptRelatedTo(
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
            referencedTogether=[],
            frequentCollaborators=[],
            relatedTopics=[],
        ),
    )


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
