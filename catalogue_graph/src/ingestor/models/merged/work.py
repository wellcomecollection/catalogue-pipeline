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
    merged_time: str
    availabilities: list[Id]
    merge_candidates: list[MergeCandidate]

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
