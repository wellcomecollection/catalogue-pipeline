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

    def write(self, file_location: str) -> None:
        pydantic_to_s3_json(self, f"{file_location}.{self.step}.json")
