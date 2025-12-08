"""Utilities for writing transformer output manifests (success & failure)."""

from __future__ import annotations

from collections.abc import Iterable
from pathlib import PurePosixPath

import smart_open
from pydantic import BaseModel

# Batch size for converting Arrow tables to Python objects before indexing
# This must result in batches of output ids that fit in the 256kb item
# limit for step function invocations (with some margin).
BATCH_SIZE = 5_000


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


def _success_lines(batches_ids: list[list[str]], job_id: str) -> Iterable[str]:
    for ids in batches_ids:
        line = SuccessBatchLine(sourceIdentifiers=ids, jobId=job_id)
        yield line.model_dump_json()


def _failure_lines(errors: list[ErrorLine]) -> Iterable[str]:
    for line in errors:
        yield line.model_dump_json()


class ManifestWriter:
    """Write success & failure manifests for the transformer.

    Success manifest: <changeset|reindex>.<job>.ids.ndjson
    Failure manifest: <changeset|reindex>.<job>.ids.failures.ndjson (only if failures)
    """

    def __init__(
        self,
        job_id: str,
        changeset_id: str | None,
        *,
        bucket: str,
        prefix: str,
    ) -> None:
        base = f"{changeset_id or 'reindex'}.{job_id}.ids"
        self.success_filename = f"{base}.ndjson"
        self.failure_filename = f"{base}.failures.ndjson"
        self.prefix = prefix
        self.bucket = bucket
        self.job_id = job_id

    def write_success(self, batches_ids: list[list[str]]) -> S3Location:
        key = PurePosixPath(self.prefix) / self.success_filename
        uri = f"s3://{self.bucket}/{key.as_posix()}"
        with smart_open.open(uri, "w", encoding="utf-8") as f:
            for line in _success_lines(batches_ids, self.job_id):
                f.write(line + "\n")
        return S3Location(bucket=self.bucket, key=key.as_posix())

    def write_failures(self, errors: list[ErrorLine]) -> S3Location:
        key = PurePosixPath(self.prefix) / self.failure_filename
        uri = f"s3://{self.bucket}/{key.as_posix()}"
        with smart_open.open(uri, "w", encoding="utf-8") as f:
            for line in _failure_lines(errors):
                f.write(line + "\n")
        return S3Location(bucket=self.bucket, key=key.as_posix())

    def build_manifest(
        self,
        *,
        job_id: str,
        batches_ids: list[list[str]],
        errors: list[ErrorLine],
        success_count: int,
    ) -> TransformerManifest:
        success_loc = self.write_success(batches_ids)
        failures_section: FailureManifest | None = None
        if errors:
            failure_loc = self.write_failures(errors)
            failures_section = FailureManifest(
                count=len(errors), error_file_location=failure_loc
            )
        return TransformerManifest(
            job_id=job_id,
            successes=SuccessManifest(
                count=success_count, batch_file_location=success_loc
            ),
            failures=failures_section,
        )
