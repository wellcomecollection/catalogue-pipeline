from pydantic import BaseModel

from utils.types import IngestorType


class IngestorStepEvent(BaseModel):
    ingestor_type: IngestorType
    pipeline_date: str
    index_date: str
    job_id: str


class IngestorTriggerLambdaEvent(IngestorStepEvent):
    pass


class IngestorLoaderLambdaEvent(IngestorStepEvent):
    start_offset: int
    end_index: int


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
