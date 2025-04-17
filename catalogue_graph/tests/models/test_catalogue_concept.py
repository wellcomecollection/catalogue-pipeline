
from models.catalogue_concept import (
    CatalogueConcept,
    CatalogueConceptIdentifier,
    ConceptsQuerySingleResult,
    RelatedConcepts,
)
from test_utils import load_json_fixture


def test_catalogue_concept_from_neptune_result() -> None:
    mock_concept = load_json_fixture("neptune/concept_query_single_alternative_labels.json")

    neptune_result = ConceptsQuerySingleResult(
        concept=mock_concept,
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
    mock_concept = load_json_fixture("neptune/concept_query_single.json")

    neptune_result = ConceptsQuerySingleResult(
        concept=mock_concept,
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
