from pydantic import BaseModel


class IngestorMonitorStepEvent(BaseModel):
    force_pass: bool = False
    report_results: bool = True


class ReporterEvent(IngestorMonitorStepEvent):
    pipeline_date: str | None = None
    index_date: str | None = None
    job_id: str | None = None
    success_count: int
