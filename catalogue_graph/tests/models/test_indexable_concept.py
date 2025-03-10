from models.catalogue_concept import CatalogueConcept, CatalogueConceptIdentifier
from models.indexable_concept import (
    ConceptDisplay,
    ConceptDisplayIdentifier,
    ConceptQuery,
    ConceptQueryIdentifier,
    IndexableConcept,
)


def test_indexable_concept_from_catalogue_concept() -> None:
    catalogue_concept = CatalogueConcept(
        id="id",
        identifiers=[
            CatalogueConceptIdentifier(value="value", identifierType="identifierType")
        ],
        label="label",
        alternativeLabels=["alternativeLabels"],
        description="description",
        type="type",
    )

    assert IndexableConcept.from_concept(catalogue_concept) == IndexableConcept(
        query=ConceptQuery(
            id="id",
            identifiers=[
                ConceptQueryIdentifier(value="value", identifierType="identifierType")
            ],
            label="label",
            alternativeLabels=["alternativeLabels"],
            type="type",
        ),
        display=ConceptDisplay(
            id="id",
            identifiers=[ConceptDisplayIdentifier(id="value", label="identifierType")],
            label="label",
            alternativeLabels=["alternativeLabels"],
            description="description",
            type="type",
        ),
    )
