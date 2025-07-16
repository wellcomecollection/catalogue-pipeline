from pydantic import BaseModel

class IngestorStepEvent(BaseModel):
    pipeline_date: str | None = None
    index_date: str | None = None
    job_id: str | None = None

class IngestorMonitorStepEvent(IngestorStepEvent):
    force_pass: bool = False
    report_results: bool = True


