"""S3 NDJSON manifest writing for the Axiell → FOLIO sync step.

These functions write success records, error records, and a metadata summary to S3 in NDJSON format —
providing an audit trail per sync job.
"""

from __future__ import annotations

import json
from datetime import UTC, datetime
from typing import Any

import structlog
from botocore.exceptions import ClientError

logger = structlog.get_logger(__name__)


def _utc_now_iso() -> str:
    """Timezone-aware UTC timestamp in ISO 8601."""
    return datetime.now(UTC).isoformat()


def flush_success_batch(
    s3_client: Any, bucket: str, job_id: str, batch: list[dict]
) -> None:
    """Append a batch of success records to the job's NDJSON manifest on S3."""
    if not batch:
        return

    key = f"manifests/{job_id}.ids.ndjson"
    ndjson_lines = "\n".join(json.dumps(record) for record in batch) + "\n"

    existing_body = b""
    try:
        existing = s3_client.get_object(Bucket=bucket, Key=key)
        existing_body = existing["Body"].read()
    except ClientError as exc:
        error_code = exc.response.get("Error", {}).get("Code")
        if error_code in ("NoSuchKey", "404", "NotFound"):
            existing_body = b""
        else:
            raise

    s3_client.put_object(
        Bucket=bucket,
        Key=key,
        Body=existing_body + ndjson_lines.encode("utf-8"),
        ContentType="application/x-ndjson",
    )
    logger.info("flushed_success_batch", job_id=job_id, key=key, count=len(batch))


def write_error_manifest(
    s3_client: Any, bucket: str, job_id: str, errors: list[dict]
) -> None:
    """Write failed-record details to a separate NDJSON file on S3."""
    if not errors:
        return
    key = f"manifests/{job_id}.ids.failures.ndjson"
    ndjson_lines = "\n".join(json.dumps(e) for e in errors)
    s3_client.put_object(
        Bucket=bucket,
        Key=key,
        Body=(ndjson_lines + "\n").encode("utf-8"),
        ContentType="application/x-ndjson",
    )
    logger.info("wrote_error_manifest", job_id=job_id, key=key, count=len(errors))


def write_metadata_manifest(
    s3_client: Any,
    bucket: str,
    job_id: str,
    total_successful: int,
    total_errors: int,
    has_errors_file: bool,
    changeset_ids: list[str] | None = None,
) -> str:
    """Write a JSON summary manifest and return its S3 URI."""
    metadata: dict[str, Any] = {
        "jobId": job_id,
        "timestamp": _utc_now_iso(),
        "summary": {
            "totalSuccessful": total_successful,
            "totalErrors": total_errors,
            "totalRecords": total_successful + total_errors,
        },
        "files": {
            "success": f"s3://{bucket}/manifests/{job_id}.ids.ndjson",
            "errors": f"s3://{bucket}/manifests/{job_id}.ids.failures.ndjson"
            if has_errors_file
            else None,
        },
    }
    if changeset_ids:
        metadata["changesetIds"] = changeset_ids
    key = f"manifests/{job_id}.manifest.json"
    s3_client.put_object(
        Bucket=bucket,
        Key=key,
        Body=json.dumps(metadata, indent=2).encode("utf-8"),
        ContentType="application/json",
    )
    logger.info("wrote_metadata_manifest", job_id=job_id, key=key)
    return f"s3://{bucket}/{key}"
