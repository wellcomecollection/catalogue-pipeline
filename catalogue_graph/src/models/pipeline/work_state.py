from datetime import datetime

from pydantic import BaseModel, field_validator

from models.pipeline.identifier import (
    SourceIdentifier,
)
from models.pipeline.serialisable import ElasticsearchModel


class WorkAncestor(ElasticsearchModel):
    title: str
    work_type: str
    depth: int
    num_children: int
    num_descendents: int


class WorkRelations(BaseModel):
    ancestors: list[WorkAncestor] = Field(default_factory=list)

    @field_validator("ancestors", mode="before")
    @classmethod
    def convert_merged_type(cls, raw_ancestors: list[dict]) -> list[dict]:
        # TODO: This is a temporary 'Series' filter which won't be needed once we remove the relation embedder service
        return [a for a in raw_ancestors if a["numChildren"] == 0]


class WorkState(ElasticsearchModel):
    source_identifier: SourceIdentifier
    source_modified_time: datetime
    modified_time: datetime
    relations: WorkRelations

    def id(self) -> str:
        raise NotImplementedError()
