import json
from typing import Any

import smart_open
from pydantic import BaseModel


class ProcessedFileRecord(BaseModel):
    """Represents metadata stored for a processed source file."""

    job_id: str
    changeset_id: str | None = None


def record_processed_file(
    job_id: str, file_location: str, changeset_id: str | None
) -> ProcessedFileRecord:
    """Persist a tracking record for a processed file to S3 ("*.loaded.json").

    Args:
        job_id: State machine execution id.
        file_location: S3 URI of the processed file (without tracking suffix).
        changeset_id: Identifier for items added/updated in this execution.

    Returns:
        A ``ProcessedFileRecord`` instance of what was written.
    """
    tracking_file_uri = f"{file_location}.loaded.json"
    record = ProcessedFileRecord(job_id=job_id, changeset_id=changeset_id)
    with smart_open.open(tracking_file_uri, "w", encoding="utf-8") as f:
        f.write(json.dumps(record.model_dump()))
    return record


def is_file_already_processed(bucket: str, key: str) -> ProcessedFileRecord | None:
    """Return the stored ``ProcessedFileRecord`` if this file was processed.

    Looks for an object at ``{key}.loaded.json`` within ``bucket``. If present the
    JSON contents are deserialised & validated with Pydantic. Any failure (missing
    object, invalid JSON, validation error, unexpected AWS error) results in ``None``.

    Args:
        bucket: S3 bucket name.
        key: S3 object key of the original file (WITHOUT the ``.loaded.json`` suffix).

    Returns:
        ``ProcessedFileRecord`` if found and valid, otherwise ``None``.
    """
    s3_client = boto3.client("s3")
    tracking_key = f"{key}.loaded.json"
    try:
        obj = s3_client.get_object(Bucket=bucket, Key=tracking_key)
        body = obj["Body"].read().decode("utf-8")
        data = json.loads(body)
        return ProcessedFileRecord.model_validate(data)
    except Exception:
        return None
