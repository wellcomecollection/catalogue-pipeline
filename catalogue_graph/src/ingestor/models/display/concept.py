from pydantic import BaseModel

from ingestor.models.shared.concept import Concept, Subject
from utils.types import ConceptType

from .identifier import DisplayIdentifier


class DisplayConcept(BaseModel):
    id: str | None = None
    label: str
    identifiers: list[DisplayIdentifier] | None = None
    type: ConceptType = "Concept"

    @staticmethod
    def from_concept(concept: Concept) -> "DisplayConcept":
        identifiers = list(DisplayIdentifier.from_all_identifiers(concept.id))

        return DisplayConcept(
            id=concept.id.canonical_id,
            label=concept.label,
            identifiers=None if len(identifiers) == 0 else identifiers,
            type=concept.type,
        )


class DisplayContributionRole(BaseModel):
    label: str
    type: str = "ContributionRole"


class DisplayContributor(BaseModel):
    agent: DisplayConcept
    roles: list[DisplayContributionRole]
    primary: bool
    type: str = "Contributor"


class DisplaySubject(DisplayConcept):
    concepts: list[DisplayConcept]
    type: ConceptType = "Subject"

    @staticmethod
    def from_subject(subject: Subject) -> "DisplaySubject":
        concept = DisplayConcept.from_concept(subject)
        return DisplaySubject(
            **concept.model_dump(),
            concepts=[DisplayConcept.from_concept(c) for c in subject.concepts],
        )


class DisplayGenre(BaseModel):
    label: str
    concepts: list[DisplayConcept]
    type: ConceptType = "Genre"
