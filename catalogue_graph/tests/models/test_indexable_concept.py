from models.catalogue_concept import (
    CatalogueConcept,
    CatalogueConceptIdentifier,
    CatalogueConceptRelatedTo,
    ConceptDescription,
)
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
        displayLabel="label",
        alternativeLabels=["alternativeLabels"],
        description=ConceptDescription(
            text="description",
            sourceLabel="nlm-mesh",
            sourceUrl="https://test.com",
        ),
        type="Concept",
        sameAs=["1234"],
        relatedConcepts=RelatedConcepts(
            relatedTo=[],
            fieldsOfWork=[
                CatalogueConceptRelatedTo(
                    label="some label",
                    id="5678",
                    relationshipType="type",
                    conceptType="Subject",
                )
            ],
            narrowerThan=[],
            broaderThan=[],
            people=[],
            referencedTogether=[],
            frequentCollaborators=[],
            relatedTopics=[],
        ),
    )

    assert IndexableConcept.from_concept(catalogue_concept) == IndexableConcept(
        query=ConceptQuery(
            id="id",
            identifiers=[
                ConceptQueryIdentifier(value="value", identifierType="nlm-mesh")
            ],
            label="label",
            alternativeLabels=["alternativeLabels"],
            type="Concept",
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
            displayLabel="label",
            alternativeLabels=["alternativeLabels"],
            description=ConceptDescription(
                text="description",
                sourceLabel="nlm-mesh",
                sourceUrl="https://test.com",
            ),
            type="Concept",
            sameAs=["1234"],
            relatedConcepts=RelatedConcepts(
                relatedTo=[],
                fieldsOfWork=[
                    CatalogueConceptRelatedTo(
                        label="some label",
                        id="5678",
                        relationshipType="type",
                        conceptType="Subject",
                    )
                ],
                narrowerThan=[],
                broaderThan=[],
                people=[],
                referencedTogether=[],
                frequentCollaborators=[],
                relatedTopics=[],
            ),
        ),
    )
