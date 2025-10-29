from pydantic import Field

from models.pipeline.id_label import Label
from models.pipeline.identifier import (
    Identifiable,
    Identified,
    Unidentifiable,
)
from models.pipeline.serialisable import SerialisableModel
from utils.types import ConceptType, RawConceptType


class Concept(SerialisableModel):
    id: Identified | Unidentifiable | Identifiable = Unidentifiable()
    label: str
    type: RawConceptType = "Concept"

    @staticmethod
    def type_to_display_type(raw_type: RawConceptType) -> ConceptType:
        # In the merged index, the 'Genre' type is called 'GenreConcept'
        if raw_type == "GenreConcept":
            return "Genre"

        return raw_type

    @property
    def display_type(self) -> ConceptType:
        return self.type_to_display_type(self.type)

    @property
    def normalised_label(self) -> str:
        return self.label.removesuffix(".")


class IdentifiedConcept(Concept):
    id: Identified

    @staticmethod
    def from_concept(concept: Concept) -> "IdentifiedConcept":
        if concept.id.canonical_id is None:
            raise TypeError(f"Concept {concept} does not have an ID.")

        return IdentifiedConcept(
            id=Identified.model_validate(concept.id.model_dump()),
            label=concept.label,
            type=concept.type,
        )


class Contributor(SerialisableModel):
    id: Identified | Unidentifiable | Identifiable = Unidentifiable()
    agent: Concept
    roles: list[Label] = []
    primary: bool = True


class Subject(Concept):
    concepts: list[Concept]
    type: RawConceptType = "Subject"


class Genre(SerialisableModel):
    label: str
    concepts: list[Concept]


class DateTimeRange(SerialisableModel):
    from_time: str = Field(alias="from")
    to_time: str = Field(alias="to")
    label: str | None = None


class Period(Concept):
    range: DateTimeRange | None = None
