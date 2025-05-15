from models.catalogue_concept import (
    CatalogueConcept,
    CatalogueConceptIdentifier,
    CatalogueConceptRelatedTo,
    ConceptsQuerySingleResult,
    RelatedConcepts,
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
        description="Mesh description",
        type="type",
        sameAs=[],
        relatedConcepts=RelatedConcepts(
            relatedTo=[],
            fieldsOfWork=[],
            narrowerThan=[],
            broaderThan=[],
            people=[],
            referencedTogether=[],
            frequentCollaborators=[],
            relatedTopics=[]
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
        related_topics=[]
    )

    assert CatalogueConcept.from_neptune_result(neptune_result) == CatalogueConcept(
        id="id",
        identifiers=[
            CatalogueConceptIdentifier(value="456", identifierType="lc-names")
        ],
        label="label",
        alternativeLabels=[],
        description="Mesh description",
        type="type",
        sameAs=[],
        relatedConcepts=RelatedConcepts(
            relatedTo=[],
            fieldsOfWork=[],
            narrowerThan=[],
            broaderThan=[],
            people=[],
            referencedTogether=[],
            frequentCollaborators=[],
            relatedTopics=[]
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
        related_topics=[]
    )

    assert CatalogueConcept.from_neptune_result(neptune_result) == CatalogueConcept(
        id="a2584ttj",
        identifiers=[
            CatalogueConceptIdentifier(value="sh85145789", identifierType="lc-subjects")
        ],
        label="Waves",
        alternativeLabels=["Mechanical waves", "Waves"],
        description="Repeated oscillation about a stable equilibrium",
        type="Concept",
        sameAs=["gcmn66yk", "a2584ttj"],
        relatedConcepts=RelatedConcepts(
            relatedTo=[
                CatalogueConceptRelatedTo(
                    label="Hilton, Violet, 1908-1969",
                    id="tzrtx26u",
                    relationshipType="has_sibling",
                )
            ],
            fieldsOfWork=[],
            narrowerThan=[],
            broaderThan=[],
            people=[],
            referencedTogether=[],
            frequentCollaborators=[],
            relatedTopics=[]
        ),
    )
