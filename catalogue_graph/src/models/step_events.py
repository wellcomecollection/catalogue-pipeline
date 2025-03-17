from pydantic import BaseModel

class IngestorMonitorStepEvent(BaseModel):
    force_pass: bool = False
    report_results: bool = True