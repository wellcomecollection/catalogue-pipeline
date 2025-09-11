from pathlib import PurePosixPath

import config
from models.events import BasePipelineEvent
from pydantic import BaseModel
from utils.types import IngestorType


class IngestorStepEvent(BasePipelineEvent):
    ingestor_type: IngestorType
    pipeline_date: str
    index_date: str
    job_id: str

    def to_s3_prefix(self) -> str: 
        parts: list[str] = [config.CATALOGUE_GRAPH_S3_BUCKET, f"{config.INGESTOR_S3_PREFIX}_{self.ingestor_type}", self.pipeline_date, self.index_date, self.job_id]
        s3_prefix = PurePosixPath(*parts)
        return str(s3_prefix)


class IngestorTriggerLambdaEvent(IngestorStepEvent):
    pass


class IngestorLoaderLambdaEvent(IngestorStepEvent):
    pass


class IngestorIndexerObject(BaseModel):
    s3_uri: str
    content_length: int | None = None
    record_count: int | None = None


class IngestorIndexerLambdaEvent(IngestorStepEvent):
    object_to_index: IngestorIndexerObject


class IngestorMonitorStepEvent(IngestorStepEvent):
    force_pass: bool = False
    report_results: bool = True


class IngestorIndexerMonitorLambdaEvent(IngestorMonitorStepEvent):
    success_count: int


class IngestorLoaderMonitorLambdaEvent(IngestorMonitorStepEvent):
    events: list[IngestorIndexerLambdaEvent]


class IngestorTriggerMonitorLambdaEvent(IngestorMonitorStepEvent):
    events: list[IngestorLoaderLambdaEvent]
