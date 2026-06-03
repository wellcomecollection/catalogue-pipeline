from models.pipeline.concept import (
    Concept,
    Contributor,
    DateTimeRange,
    Genre,
    Period,
    Subject,
)
from models.pipeline.identifier import Identified, Unidentifiable
from utils.types import RawConceptType

from .identifiers import create_source_identifier
from .random import random_alphanumeric, random_canonical_id


def create_concept(
    canonical_id: str | None = None,
    label: str | None = None,
    concept_type: RawConceptType = "Concept",
    identifier_ontology_type: str = "Concept",
) -> Concept:
    return Concept(
        id=Identified(
            canonical_id=canonical_id or random_canonical_id(),
            source_identifier=create_source_identifier(
                ontology_type=identifier_ontology_type
            ),
        ),
        label=label or random_alphanumeric(15),
        type=concept_type,
    )


def create_genre_concept(
    canonical_id: str | None = None, label: str | None = None
) -> Concept:
    return create_concept(
        canonical_id,
        label,
        concept_type="GenreConcept",
        identifier_ontology_type="Genre",
    )


def create_genre(label: str, concepts: list[Concept] | None = None) -> Genre:
    if concepts is None:
        concepts = [
            create_genre_concept(label=label),
            create_concept(),
            create_concept(),
        ]
    return Genre(label=label, concepts=concepts)


def create_subject(label: str, concepts: list[Concept] | None = None) -> Subject:
    if concepts is None:
        concepts = [create_concept(), create_concept(), create_concept()]
    return Subject(label=label, concepts=concepts)


def create_contributor(label: str, concept_type: RawConceptType) -> Contributor:
    return Contributor(
        id=Unidentifiable(),
        agent=Concept(id=Unidentifiable(), label=label, type=concept_type),
        roles=[],
        primary=True,
    )


def create_period_for_year(year: str) -> Period:
    return Period(
        id=Unidentifiable(),
        label=year,
        range=DateTimeRange(
            **{
                "from": f"{year}-01-01T00:00:00Z",
                "to": f"{year}-12-31T23:59:59Z",
                "label": year,
            }
        ),
    )


def create_period_for_year_range(start_year: str, end_year: str) -> Period:
    return Period(
        id=Unidentifiable(),
        label=f"{start_year}-{end_year}",
        range=DateTimeRange(
            **{
                "from": f"{start_year}-01-01T00:00:00Z",
                "to": f"{end_year}-12-31T23:59:59Z",
                "label": f"{start_year}-{end_year}",
            }
        ),
    )
