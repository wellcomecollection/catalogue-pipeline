from pydantic import Field

from ingestor.models.shared.merge_candidate import MergeCandidate
from models.pipeline.work import DeletedWork, VisibleWork, Work
from models.pipeline.work_state import WorkState


class SourceWorkState(WorkState):
    # We don't need the internal_work_stubs in python, but to allow
    # deserialisation in the scala pipeline, we include them here.
    internal_work_stubs: list[str] = Field(default_factory=list)
    merge_candidates: list[MergeCandidate] = Field(default_factory=list)

    def id(self) -> str:
        return str(self.source_identifier)


class SourceWork(Work):
    state: SourceWorkState


class VisibleSourceWork(VisibleWork, SourceWork):
    pass


class DeletedSourceWork(DeletedWork, SourceWork):
    pass
