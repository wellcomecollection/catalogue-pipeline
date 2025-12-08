from typing import Any

from pydantic import BaseModel

from utils.aws import pydantic_to_s3_json


class ProcessedFileRecord(BaseModel):
    """Represents metadata stored for a processed source file.

    step: identifies which pipeline step produced this record (e.g. "loaded", "transformed").
    payload: arbitrary dict of step output (event or result model_dump).
    """

    job_id: str
    step: str
    payload: dict[str, Any] | None = None

    def get(self, key: str, default: Any = None) -> Any:  # convenience accessor
        if self.payload and key in self.payload:
            return self.payload[key]
        return default


def record_processed_file(
    job_id: str,
    file_location: str,
    step: str,
    payload_obj: BaseModel | None,
) -> ProcessedFileRecord:
    payload = payload_obj.model_dump() if payload_obj else None
    record = ProcessedFileRecord(job_id=job_id, step=step, payload=payload)

    pydantic_to_s3_json(record, f"{file_location}.{step}.json")
    return record
