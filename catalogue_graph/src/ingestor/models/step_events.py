from pydantic import BaseModel

from utils.types import IngestorType


class IngestorStepEvent(BaseModel):
    pipeline_date: str | None = None
    index_date: str | None = None
    job_id: str | None = None


class IngestorTriggerLambdaEvent(IngestorStepEvent):
    ingestor_type: IngestorType


class IngestorLoaderLambdaEvent(IngestorTriggerLambdaEvent):
    start_offset: int
    end_index: int


class IngestorMonitorStepEvent(IngestorStepEvent):
    force_pass: bool = False
    report_results: bool = True


class IngestorIndexerObject(BaseModel):
    s3_uri: str
    content_length: int | None = None
    record_count: int | None = None


class IngestorIndexerLambdaEvent(IngestorStepEvent):
    object_to_index: IngestorIndexerObject


class IngestorIndexerMonitorLambdaEvent(IngestorMonitorStepEvent):
    success_count: int


class IngestorLoaderMonitorLambdaEvent(IngestorMonitorStepEvent):
    events: list[IngestorIndexerLambdaEvent]


class IngestorTriggerMonitorLambdaEvent(IngestorMonitorStepEvent):
    events: list[IngestorLoaderLambdaEvent]
