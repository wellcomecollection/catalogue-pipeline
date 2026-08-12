"""
Creates example concept documents for catalogue API tests using deterministic random data via a seeded RNG.

Run from the catalogue_graph directory:
    uv run python -m document_generators.create_test_concept_documents
"""

from freezegun import freeze_time

from ingestor.models.display.identifier import DisplayIdentifier
from ingestor.models.display.location import DisplayDigitalLocation
from ingestor.models.display.location_type import DisplayLocationType
from ingestor.models.indexable.concept import (
    ConceptDescription,
    ConceptDisplay,
    ConceptIdentifier,
    ConceptQuery,
    ConceptRelatedTo,
    IndexableConcept,
    RelatedConcepts,
)
from ingestor.transformers.raw_concept import get_source_concept_url
from models.pipeline.id_label import Id
from models.pipeline.identifier import SourceIdentifier
from utils.types import ConceptType

from .generators import random_alphanumeric, random_canonical_id, reset
from .utils import TEST_DOCUMENTS_DIR, save_documents


def create_identifier_pair(
    source: str, value: str | None = None
) -> tuple[ConceptIdentifier, DisplayIdentifier]:
    """Build matching query/display identifiers, as the concepts transformer does."""
    value = value or random_alphanumeric(9).lower()
    query_identifier = ConceptIdentifier(value=value, identifierType=source)
    display_identifier = DisplayIdentifier.from_source_identifier(
        SourceIdentifier(
            value=value, identifier_type=Id(id=source), ontology_type="Concept"
        )
    )
    return query_identifier, display_identifier


def create_description(source: str = "wikidata") -> ConceptDescription:
    return ConceptDescription(
        text=random_alphanumeric(40),
        sourceLabel=source,
        sourceUrl=get_source_concept_url(random_alphanumeric(9).lower(), source),
    )


def create_related_concept(
    label: str,
    concept_type: str = "Concept",
    relationship_type: str | None = None,
) -> ConceptRelatedTo:
    return ConceptRelatedTo(
        label=label,
        id=random_canonical_id(),
        relationshipType=relationship_type,
        conceptType=concept_type,
    )


def create_related_concepts(**overrides: list[ConceptRelatedTo]) -> RelatedConcepts:
    fields: dict[str, list[ConceptRelatedTo]] = {
        "relatedTo": [],
        "fieldsOfWork": [],
        "narrowerThan": [],
        "broaderThan": [],
        "people": [],
        "frequentCollaborators": [],
        "relatedTopics": [],
        "foundedBy": [],
    }
    fields.update(overrides)
    return RelatedConcepts(**fields)


def create_display_image() -> DisplayDigitalLocation:
    """Mirrors the concepts transformer, which builds iiif-image locations from weco-authority image URLs."""
    image_ref = f"V{random_alphanumeric(7).lower()}"
    return DisplayDigitalLocation(
        url=f"https://iiif.wellcomecollection.org/image/{image_ref}/full/full/0/default.jpg",
        locationType=DisplayLocationType.from_id("iiif-image"),
        accessConditions=[],
    )


@freeze_time("2001-01-01T01:01:01Z")
def create_indexable_concept(
    label: str,
    concept_type: ConceptType,
    display_label: str | None = None,
    identifier_sources: list[str] | None = None,
    alternative_labels: list[str] | None = None,
    description: ConceptDescription | None = None,
    related_concepts: RelatedConcepts | None = None,
    same_as: list[str] | None = None,
    display_images: list[DisplayDigitalLocation] | None = None,
) -> IndexableConcept:
    canonical_id = random_canonical_id()
    identifier_pairs = [
        create_identifier_pair(source) for source in (identifier_sources or [])
    ]
    alternative_labels = alternative_labels or []

    query = ConceptQuery(
        id=canonical_id,
        identifiers=[query_identifier for query_identifier, _ in identifier_pairs],
        label=label,
        alternativeLabels=alternative_labels,
        type=concept_type,
    )
    display = ConceptDisplay(
        id=canonical_id,
        identifiers=[display_identifier for _, display_identifier in identifier_pairs],
        label=label,
        displayLabel=display_label or label,
        alternativeLabels=alternative_labels,
        description=description,
        type=concept_type,
        relatedConcepts=related_concepts or create_related_concepts(),
        sameAs=same_as or [],
        displayImages=display_images or [],
    )
    return IndexableConcept(query=query, display=display)


# ---------- Test document generators ----------


def create_person_concept() -> None:
    concept = create_indexable_concept(
        label="Pemberton, Petronella",
        display_label="Petronella Pemberton",
        concept_type="Person",
        identifier_sources=["lc-names", "wikidata", "viaf"],
        alternative_labels=["P. Pemberton", "Pemberton, Petronella, 1888-1976"],
        description=create_description(source="wikidata"),
        related_concepts=create_related_concepts(
            relatedTo=[
                create_related_concept(
                    "Pemberton, Percival",
                    concept_type="Person",
                    relationship_type="has_sibling",
                ),
                create_related_concept("Aardvark antics"),
            ],
            fieldsOfWork=[
                create_related_concept("Badger ballet"),
                create_related_concept("Capybara cartography"),
            ],
            frequentCollaborators=[
                create_related_concept("Ostrich, Olga", concept_type="Person"),
            ],
        ),
        same_as=[random_canonical_id(), random_canonical_id()],
        display_images=[create_display_image()],
    )
    save_documents(
        [concept],
        description="a person concept with every field populated",
        doc_id="concepts.person",
    )


def create_organisation_concept() -> None:
    concept = create_indexable_concept(
        label="Dodo Preservation Society",
        concept_type="Organisation",
        identifier_sources=["lc-names"],
        description=create_description(source="weco-authority"),
        related_concepts=create_related_concepts(
            foundedBy=[
                create_related_concept(
                    "Pemberton, Petronella",
                    concept_type="Person",
                    relationship_type="has_founder",
                ),
            ],
            people=[
                create_related_concept("Ostrich, Olga", concept_type="Person"),
                create_related_concept("Flamingo, Florence", concept_type="Person"),
            ],
        ),
    )
    save_documents(
        [concept],
        description="an organisation concept with founders and people",
        doc_id="concepts.organisation",
    )


def create_genre_concept_document() -> None:
    concept = create_indexable_concept(
        label="Emu engravings",
        concept_type="Genre",
        identifier_sources=["lc-gmgpc"],
        related_concepts=create_related_concepts(
            narrowerThan=[create_related_concept("Engravings", concept_type="Genre")],
            broaderThan=[
                create_related_concept("Wood emu engravings", concept_type="Genre"),
                create_related_concept("Steel emu engravings", concept_type="Genre"),
            ],
            relatedTopics=[create_related_concept("Flightless finery")],
        ),
    )
    save_documents(
        [concept],
        description="a genre concept with a hierarchy but no description",
        doc_id="concepts.genre",
    )


def create_place_concept() -> None:
    concept = create_indexable_concept(
        label="Lower Quokka-on-the-Wold",
        concept_type="Place",
        identifier_sources=["lc-subjects", "nlm-mesh"],
        alternative_labels=["Quokka-on-the-Wold, Lower"],
        description=create_description(source="wikidata"),
        related_concepts=create_related_concepts(
            relatedTo=[create_related_concept("Upper Quokka", concept_type="Place")],
        ),
        same_as=[random_canonical_id()],
    )
    save_documents(
        [concept],
        description="a place concept with identifiers from multiple sources",
        doc_id="concepts.place",
    )


def create_period_concept() -> None:
    concept = create_indexable_concept(
        label="The Wombat Renaissance",
        concept_type="Period",
        identifier_sources=["label-derived"],
        related_concepts=create_related_concepts(
            relatedTopics=[create_related_concept("Marsupial modernism")],
        ),
    )
    save_documents(
        [concept],
        description="a period concept with a label-derived identifier",
        doc_id="concepts.period",
    )


def create_minimal_concept() -> None:
    concept = create_indexable_concept(
        label="Nondescript notions",
        concept_type="Concept",
    )
    save_documents(
        [concept],
        description="a minimal concept with no identifiers, description, or related concepts",
        doc_id="concepts.minimal",
    )


def generate_all() -> None:
    reset()

    create_person_concept()
    create_organisation_concept()
    create_genre_concept_document()
    create_place_concept()
    create_period_concept()
    create_minimal_concept()

    print(f"Test documents written to {TEST_DOCUMENTS_DIR}")


if __name__ == "__main__":
    generate_all()
