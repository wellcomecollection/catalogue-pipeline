from pydantic import BaseModel, Field, field_validator

from models.graph_node import BaseNode, Concept, SourceConcept, Work


class NeptuneBaseNode(BaseModel):
    id: str = Field(alias="~id")
    labels: list[str] = Field(alias="~labels")
    properties: BaseNode = Field(alias="~properties")

    @field_validator("properties", mode="before")
    @classmethod
    def process_lists(cls, data: dict) -> dict:
        data = data.copy()
        list_fields = ["alternative_labels", "alternative_ids", "image_urls"]
        for field in list_fields:
            if field in data:
                # The catalogue graph stores lists as strings, with individual items separated by `||`.
                data[field] = data[field].split("||")

        return data


class ConceptNode(NeptuneBaseNode):
    properties: Concept = Field(alias="~properties")


class SourceConceptNode(NeptuneBaseNode):
    properties: SourceConcept = Field(alias="~properties")


class WorkNode(NeptuneBaseNode):
    properties: Work = Field(alias="~properties")
