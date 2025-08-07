from pydantic import BaseModel

from ingestor.models.denormalised.work import Concept
from utils.types import ConceptType

from .identifier import DisplayIdentifier


class DisplayConcept(BaseModel):
    id: str | None = None
    label: str
    identifiers: list[DisplayIdentifier] | None = None
    type: ConceptType = "Concept"

    @staticmethod
    def from_concept(concept: Concept) -> "DisplayConcept":
        # TODO: Should we remove the suffix here?
        return DisplayConcept(
            id=concept.id.canonical_id,
            label=concept.label.removesuffix("."),
            identifiers=DisplayIdentifier.from_all_identifiers(concept.id),
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


class DisplayGenre(BaseModel):
    label: str
    concepts: list[DisplayConcept]
    type: ConceptType = "Genre"
