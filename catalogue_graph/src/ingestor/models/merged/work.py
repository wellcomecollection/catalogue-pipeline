from datetime import datetime

from pydantic import BaseModel, field_validator, model_validator

from ingestor.models.shared.deleted_reason import DeletedReason
from ingestor.models.shared.invisible_reason import InvisibleReason
from ingestor.models.shared.merge_candidate import MergeCandidate
from models.pipeline.id_label import Id
from models.pipeline.identifier import (
    Identified,
    SourceIdentifier,
)
from models.pipeline.serialisable import ElasticsearchModel
from models.pipeline.work_data import WorkData
from utils.types import DisplayWorkType, WorkStatus


class WorkAncestor(ElasticsearchModel):
    title: str
    work_type: str
    depth: int
    num_children: int
    num_descendents: int


class WorkRelations(BaseModel):
    ancestors: list[WorkAncestor] = []

    @field_validator("ancestors", mode="before")
    @classmethod
    def convert_merged_type(cls, raw_ancestors: list[dict]) -> list[dict]:
        # TODO: This is a temporary 'Series' filter which won't be needed once we remove the relation embedder service
        return [a for a in raw_ancestors if a["numChildren"] == 0]


class MergedWorkData(WorkData):
    @property
    def display_work_type(self) -> DisplayWorkType:
        if self.work_type == "Standard":
            return "Work"

        return self.work_type


class SourceWorkData(WorkData):
    pass


class WorkState(ElasticsearchModel):
    source_identifier: SourceIdentifier
    modified_time: datetime
    relations: WorkRelations

    def id(self) -> str:
        raise NotImplementedError()


class MergedWorkState(WorkState):
    canonical_id: str
    merged_time: datetime
    source_modified_time: datetime
    availabilities: list[Id]
    merge_candidates: list[MergeCandidate]

    @model_validator(mode="before")
    @classmethod
    def _set_modified_time(cls, data: dict) -> dict:
        if isinstance(data, dict) and ("merged_time" in data or "mergedTime" in data):
            d = dict(data)
            # Prefer snake_case if present else camelCase
            merged_val = d.get("merged_time", d.get("mergedTime"))
            d["modified_time"] = merged_val
            return d
        return data

    def id(self) -> str:
        return self.canonical_id


class SourceWorkState(WorkState):
    pass


class Work(ElasticsearchModel):
    state: MergedWorkState | SourceWorkState
    version: int
    type: WorkStatus
    data: MergedWorkData | SourceWorkData | None = None

    @staticmethod
    def from_raw_document(work: dict) -> "Work":
        if work["type"] == "Visible":
            return VisibleWork.model_validate(work)
        if work["type"] == "Invisible":
            return InvisibleWork.model_validate(work)
        if work["type"] == "Deleted":
            return DeletedWork.model_validate(work)

        raise ValueError(f"Unknown work type '{work['type']}' for work {work}")


class VisibleWork(Work):
    type: WorkStatus = "Visible"
    redirect_sources: list[Identified]


class InvisibleWork(Work):
    type: WorkStatus = "Invisible"
    invisibility_reasons: list[InvisibleReason]


class DeletedWork(Work):
    type: WorkStatus = "Deleted"
    deleted_reason: DeletedReason


class MergedWork(ElasticsearchModel):
    state: MergedWorkState
    version: int
    type: WorkStatus

    @staticmethod
    def from_raw_document(work: dict) -> "MergedWork":
        if work["type"] == "Visible":
            return VisibleMergedWork.model_validate(work)
        if work["type"] == "Invisible":
            return InvisibleMergedWork.model_validate(work)
        if work["type"] == "Redirected":
            return RedirectedMergedWork.model_validate(work)
        if work["type"] == "Deleted":
            return DeletedMergedWork.model_validate(work)

        raise ValueError(f"Unknown work type '{work['type']}' for work {work}")


class VisibleMergedWork(MergedWork):
    data: MergedWorkData
    redirect_sources: list[Identified]


class InvisibleMergedWork(MergedWork):
    invisibility_reasons: list[InvisibleReason]


class DeletedMergedWork(MergedWork):
    deleted_reason: DeletedReason


class RedirectedMergedWork(MergedWork):
    redirect_target: Identified
