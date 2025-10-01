"""Manifest-related pydantic models shared by transformer & other steps."""

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


class TransformerManifest(BaseModel):
    job_id: str
    successes: SuccessManifest
    failures: FailureManifest | None = None


# These are consumed by a Scala service, so for convenience
# we keep the field names in camelCase
class SuccessBatchLine(BaseModel):
    sourceIdentifiers: list[str]
    jobId: str


class ErrorLine(BaseModel):
    id: str
    message: str
