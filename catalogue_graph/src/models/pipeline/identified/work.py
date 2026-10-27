from pydantic import Field

from ingestor.models.shared.merge_candidate import MergeCandidate
from models.pipeline.identifier import SourceIdentifier
from models.pipeline.serialisable import SerialisableModel
from models.pipeline.work import DeletedWork, InvisibleWork, VisibleWork, Work
from models.pipeline.work_data import WorkData
from models.pipeline.work_state import WorkState


class InternalWorkStub(SerialisableModel):
    """A work nested inside a TEI work, minted with its own canonical id."""

    source_identifier: SourceIdentifier
    canonical_id: str
    work_data: WorkData


class IdentifiedWorkState(WorkState):
    canonical_id: str
    modified_time: str | None = None
    merge_candidates: list[MergeCandidate] = Field(default_factory=list)
    internal_work_stubs: list[InternalWorkStub] = Field(default_factory=list)
    removed_internal_work_stubs: list[InternalWorkStub] = Field(default_factory=list)

    def id(self) -> str:
        return self.canonical_id


class IdentifiedWork(Work):
    state: IdentifiedWorkState

    @staticmethod
    def from_raw_document(work: dict) -> "IdentifiedWork":
        if work["type"] == "Visible":
            return VisibleIdentifiedWork.model_validate(work)
        if work["type"] == "Invisible":
            return InvisibleIdentifiedWork.model_validate(work)
        if work["type"] == "Deleted":
            return DeletedIdentifiedWork.model_validate(work)

        raise ValueError(f"Unknown work type '{work['type']}' for work {work}")


class VisibleIdentifiedWork(VisibleWork, IdentifiedWork):
    pass


class InvisibleIdentifiedWork(InvisibleWork, IdentifiedWork):
    pass


class DeletedIdentifiedWork(DeletedWork, IdentifiedWork):
    pass
