from ingestor.models.shared.deleted_reason import DeletedReason
from ingestor.models.shared.invisible_reason import InvisibleReason
from models.pipeline.identifier import (
    Identified,
)
from models.pipeline.serialisable import ElasticsearchModel
from models.pipeline.work_data import WorkData
from utils.types import WorkStatus


class Work(ElasticsearchModel):
    version: int
    type: WorkStatus
    data: WorkData = WorkData()


class VisibleWork(Work):
    type: WorkStatus = "Visible"
    redirect_sources: list[Identified]


class InvisibleWork(Work):
    type: WorkStatus = "Invisible"
    invisibility_reasons: list[InvisibleReason]


class DeletedWork(Work):
    type: WorkStatus = "Deleted"
    deleted_reason: DeletedReason


class RedirectedWork(ElasticsearchModel):
    type: WorkStatus = "Redirected"
    redirect_target: Identified
