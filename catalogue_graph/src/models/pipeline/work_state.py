from pydantic import Field

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


class WorkState(SerialisableModel):
    source_identifier: SourceIdentifier
    source_modified_time: str
    modified_time: str
    relations: WorkRelations | None = None

    def id(self) -> str:
        raise NotImplementedError()
