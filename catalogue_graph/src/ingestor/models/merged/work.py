from datetime import datetime

from pydantic import model_validator

from ingestor.models.shared.merge_candidate import MergeCandidate
from models.pipeline.id_label import Id
from models.pipeline.work import (
    DeletedWork,
    InvisibleWork,
    RedirectedWork,
    VisibleWork,
    Work,
)
from models.pipeline.work_state import WorkState


class MergedWorkState(WorkState):
    canonical_id: str
    merged_time: datetime
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


class MergedWork(Work):
    state: MergedWorkState

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


class VisibleMergedWork(VisibleWork, MergedWork):
    pass


class InvisibleMergedWork(InvisibleWork, MergedWork):
    pass


class DeletedMergedWork(DeletedWork, MergedWork):
    pass


class RedirectedMergedWork(RedirectedWork, MergedWork):
    pass
