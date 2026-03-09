"""Shared manifest models for pipeline steps that write results to S3.

Used by the adapter transformer and the ID minter to produce S3-backed
manifests consumed by downstream Step Function Map states.
"""

from __future__ import annotations

from pydantic import BaseModel


class S3Location(BaseModel):
    bucket: str
    key: str


class SuccessManifest(BaseModel):
    count: int
    batch_file_location: S3Location


class FailureManifest(BaseModel):
    count: int
    error_file_location: S3Location


# These are consumed by a Scala service, so for convenience
# we keep the field names in camelCase
class BatchLine(BaseModel):
    """Base for a line in the success batch NDJSON file."""

    jobId: str


class StepManifest(BaseModel):
    """Base manifest returned by any pipeline step that writes results to S3."""

    job_id: str
    successes: SuccessManifest
    failures: FailureManifest | None = None
