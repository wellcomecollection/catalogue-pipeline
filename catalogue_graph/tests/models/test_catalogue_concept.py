from test_ingestor_loader import (
    get_mock_neptune_concept_query_response,
)

from models.catalogue_concept import (
    CatalogueConcept,
    CatalogueConceptIdentifier,
    ConceptsQuerySingleResult,
    RelatedConcepts,
)


def test_catalogue_concept_from_neptune_result() -> None:
    mock_concept_response = get_mock_neptune_concept_query_response(True)
    concept = mock_concept_response["results"][0]

    neptune_result = ConceptsQuerySingleResult(
        concept=concept,
        related_to=[],
        fields_of_work=[],
        narrower_than=[],
        broader_than=[],
        people=[],
        referenced_together=[],
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
            relatedTo=[
                # CatalogueConceptRelatedTo(label='related label', id='123', relationshipType='')
            ],
            fieldsOfWork=[],
            narrowerThan=[],
            broaderThan=[],
            people=[],
            referencedTogether=[],
        ),
    )


def test_catalogue_concept_from_neptune_result_without_alternative_labels() -> None:
    mock_concept_response = get_mock_neptune_concept_query_response(False)
    concept = mock_concept_response["results"][0]

    neptune_result = ConceptsQuerySingleResult(
        concept=concept,
        related_to=[],
        fields_of_work=[],
        narrower_than=[],
        broader_than=[],
        people=[],
        referenced_together=[],
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
        ),
    )
