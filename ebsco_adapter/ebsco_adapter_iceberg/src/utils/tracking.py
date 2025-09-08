import json
from typing import Any

import smart_open
from pydantic import BaseModel


class ProcessedFileRecord(BaseModel):
    """Represents metadata stored for a processed source file.

    step: identifies which pipeline step produced this record (e.g. "loaded", "transformed").
    """

    job_id: str
    step: str
    changeset_id: str | None = None


def record_processed_file(
    job_id: str,
    file_location: str,
    changeset_id: str | None,
    step: str,
) -> ProcessedFileRecord:
    """Persist a tracking record for a processed file to S3 ("*.<step>.json").

    Args:
        job_id: State machine execution id.
        file_location: S3 URI of the processed file (without tracking suffix).
        changeset_id: Identifier for items added/updated in this execution.
        step: Logical pipeline step name (e.g. "loaded", "transformed").

    Returns:
        A ``ProcessedFileRecord`` instance of what was written.
    """
    tracking_file_uri = f"{file_location}.{step}.json"
    record = ProcessedFileRecord(job_id=job_id, changeset_id=changeset_id, step=step)
    with smart_open.open(tracking_file_uri, "w", encoding="utf-8") as f:
        f.write(json.dumps(record.model_dump()))
    return record


def is_file_already_processed(
    file_location: str, step: str = "loaded"
) -> ProcessedFileRecord | None:
    """Return the stored ``ProcessedFileRecord`` for a step if this file was processed.

    Accepts the base *file* S3 URI (e.g. ``s3://bucket/path/file.xml``) and attempts
    to read ``<file>.{step}.json`` using ``smart_open``. Any error (missing object,
    invalid JSON, validation issues) results in ``None``.
    """
    tracking_file_uri = f"{file_location}.{step}.json"
    try:
        with smart_open.open(tracking_file_uri, "r", encoding="utf-8") as f:
            data = json.loads(f.read())
        return ProcessedFileRecord.model_validate(data)
    except Exception:
        return None
