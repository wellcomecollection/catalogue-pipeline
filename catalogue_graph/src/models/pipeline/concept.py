from datetime import datetime
from typing import Literal

from pydantic import Field, field_validator

from models.pipeline.id_label import Label
from models.pipeline.identifier import (
    Identifiable,
    Identified,
    Unidentifiable,
)
from models.pipeline.serialisable import SerialisableModel
from utils.types import ConceptType


class Concept(SerialisableModel):
    id: Identifiable | Identified | Unidentifiable = Unidentifiable()
    label: str
    type: ConceptType = "Concept"

    @field_validator("type", mode="before")
    @classmethod
    def convert_merged_type(
        cls, value: ConceptType | Literal["GenreConcept"]
    ) -> ConceptType:
        # In the merged index, the 'Genre' type is called 'GenreConcept'
        if value == "GenreConcept":
            return "Genre"

        return value

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
    id: Identifiable | Identified | Unidentifiable = Unidentifiable()
    agent: Concept
    roles: list[Label] = []
    primary: bool = True


class Subject(Concept):
    concepts: list[Concept]
    type: ConceptType = "Subject"


class Genre(SerialisableModel):
    label: str
    concepts: list[Concept]


class DateTimeRange(SerialisableModel):
    from_time: datetime = Field(alias="from")
    to_time: datetime = Field(alias="to")
    label: str | None = None


class Period(Concept):
    range: DateTimeRange | None = None
