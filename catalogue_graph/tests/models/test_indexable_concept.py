from models.catalogue_concept import CatalogueConcept, CatalogueConceptIdentifier
from models.indexable_concept import (
    ConceptDisplay,
    ConceptDisplayIdentifier,
    ConceptDisplayIdentifierType,
    ConceptQuery,
    ConceptQueryIdentifier,
    IndexableConcept,
    RelatedConcepts,
)


def test_indexable_concept_from_catalogue_concept() -> None:
    catalogue_concept = CatalogueConcept(
        id="id",
        identifiers=[
            CatalogueConceptIdentifier(value="value", identifierType="nlm-mesh")
        ],
        label="label",
        alternativeLabels=["alternativeLabels"],
        description="description",
        type="type",
        sameAs=[],
        relatedConcepts=RelatedConcepts(
            relatedTo = [],
            fieldsOfWork = [],
            narrowerThan = [],
            broaderThan = [],
            people = [],
            referencedTogether = []
        )
    )

    assert IndexableConcept.from_concept(catalogue_concept) == IndexableConcept(
        query=ConceptQuery(
            id="id",
            identifiers=[
                ConceptQueryIdentifier(value="value", identifierType="nlm-mesh")
            ],
            label="label",
            alternativeLabels=["alternativeLabels"],
            type="type",
        ),
        display=ConceptDisplay(
            id="id",
            identifiers=[
                ConceptDisplayIdentifier(
                    value="value",
                    identifierType=ConceptDisplayIdentifierType(
                        id="nlm-mesh",
                        label="Medical Subject Headings (MeSH) identifier",
                        type="IdentifierType",
                    ),
                )
            ],
            label="label",
            alternativeLabels=["alternativeLabels"],
            description="description",
            type="type",
            sameAs=[],
            relatedConcepts=RelatedConcepts(
                relatedTo = [],
                fieldsOfWork = [],
                narrowerThan = [],
                broaderThan = [],
                people = [],
                referencedTogether = []
            )
        ),
    )
