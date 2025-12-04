from pydantic import BaseModel

from models.pipeline.concept import Concept
from models.pipeline.serialisable import ElasticsearchModel
from utils.types import ConceptType

from .identifier import DisplayIdentifier


class DisplayConcept(ElasticsearchModel):
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
            type=concept.display_type,
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
