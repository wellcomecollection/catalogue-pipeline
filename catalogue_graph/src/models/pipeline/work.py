from ingestor.models.shared.deleted_reason import DeletedReason
from ingestor.models.shared.invisible_reason import InvisibleReason
from models.pipeline.identifier import (
    Identified,
)
from models.pipeline.serialisable import SerialisableModel
from models.pipeline.work_data import Field, WorkData
from utils.types import WorkStatus


class Work(SerialisableModel):
    version: int
    type: WorkStatus
    data: WorkData = WorkData()


class VisibleWork(Work):
    type: WorkStatus = "Visible"
    redirect_sources: list[Identified] = Field(default_factory=list)


class InvisibleWork(Work):
    type: WorkStatus = "Invisible"
    invisibility_reasons: list[InvisibleReason]


class DeletedWork(Work):
    type: WorkStatus = "Deleted"
    deleted_reason: DeletedReason


class RedirectedWork(SerialisableModel):
    type: WorkStatus = "Redirected"
    redirect_target: Identified


ALL_WORK_STATUSES = (
    VisibleWork,
    InvisibleWork,
    DeletedWork,
    RedirectedWork,
)
