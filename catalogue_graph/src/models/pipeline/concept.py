from typing import Literal

from pydantic import BaseModel, Field, field_validator

from utils.types import ConceptType

from models.pipeline.id_label import Label

from models.pipeline.identifier import Identifiers, Unidentifiable


class Concept(BaseModel):
    id: Identifiers | Unidentifiable = Unidentifiable()
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


class Contributor(BaseModel):
    agent: Concept
    roles: list[Label] = []
    primary: bool = True


class Subject(Concept):
    concepts: list[Concept]
    type: ConceptType = "Subject"


class Genre(BaseModel):
    label: str
    concepts: list[Concept]


class DateTimeRange(BaseModel):
    from_time: str = Field(alias="from")
    to_time: str = Field(alias="to")
    label: str | None = None


class Period(Concept):
    range: DateTimeRange | None = None
