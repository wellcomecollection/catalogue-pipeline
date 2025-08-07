
from models.graph_node import BaseNode, Concept, SourceConcept, Work
from pydantic import BaseModel, Field, model_validator


class NeptuneBaseNode(BaseModel):
    id: str = Field(alias="~id")
    labels: list[str] = Field(alias="~labels")
    properties: BaseNode = Field(alias="~properties")

    @model_validator(mode="before")
    @classmethod
    def process_lists(cls, data):
        list_fields = ["alternative_labels", "alternative_ids"]
        for field in list_fields:
            if field in data["~properties"]:
                data["~properties"][field] = data["~properties"][field].split("||")

        return data


class ConceptNode(NeptuneBaseNode):
    properties: Concept = Field(alias="~properties")


class SourceConceptNode(NeptuneBaseNode):
    properties: SourceConcept = Field(alias="~properties")


class WorkNode(NeptuneBaseNode):
    properties: Work = Field(alias="~properties")
