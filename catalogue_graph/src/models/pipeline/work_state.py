from pydantic import Field, field_validator

from models.pipeline.identifier import (
    SourceIdentifier,
)
from models.pipeline.serialisable import SerialisableModel


class WorkAncestor(SerialisableModel):
    title: str
    work_type: str
    depth: int
    num_children: int
    num_descendents: int


class WorkRelations(SerialisableModel):
    ancestors: list[WorkAncestor] = Field(default_factory=list)

    @field_validator("ancestors", mode="before")
    @classmethod
    def convert_merged_type(cls, raw_ancestors: list[dict]) -> list[dict]:
        # TODO: This is a temporary 'Series' filter which won't be needed once we remove the relation embedder service
        return [a for a in raw_ancestors if a["numChildren"] == 0]


class WorkState(SerialisableModel):
    source_identifier: SourceIdentifier
    source_modified_time: str
    modified_time: str
    relations: WorkRelations | None = None

    def id(self) -> str:
        raise NotImplementedError()
