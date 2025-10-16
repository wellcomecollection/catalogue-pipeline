from pydantic import BaseModel

from models.pipeline.identifier import SourceIdentifier
from models.pipeline.serialisable import ElasticsearchModel
from models.pipeline.work_data import WorkData


class BaseWork(BaseModel):
    source_identifier: SourceIdentifier


class DeletedWork(ElasticsearchModel, BaseWork):
    deleted_reason: str


class SourceWork(WorkData, BaseWork):
    pass
