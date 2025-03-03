from models.indexable_concept import IndexableConcept, ConceptQuery, ConceptQueryIdentifier, ConceptDisplay, ConceptDisplayIdentifier
from models.catalogue_concept import CatalogueConcept, CatalogueConceptIdentifier

def test_indexable_concept_from_catalogue_concept():
    catalogue_concept = CatalogueConcept(
        id="id",
        identifiers=[
            CatalogueConceptIdentifier(
                value="value",
                identifierType="identifierType"
            )
        ],
        label="label",
        alternativeLabels=["alternativeLabels"],
        type="type"
    )

    assert IndexableConcept.from_concept(catalogue_concept) == IndexableConcept(
        query=ConceptQuery(
            id="id",
            identifiers=[
                ConceptQueryIdentifier(
                    value="value",
                    identifierType="identifierType"
                )
            ],
            label="label",
            alternativeLabels=["alternativeLabels"],
            type="type"
        ),
        display=ConceptDisplay(
            id="id",
            identifiers=[
                ConceptDisplayIdentifier(
                    value="value",
                    identifierType="identifierType"
                )
            ],
            label="label",
            alternativeLabels=["alternativeLabels"],
            type="type"
        )
    )
