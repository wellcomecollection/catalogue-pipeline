"""Utilities for writing transformer output manifests (success & failure)."""

from __future__ import annotations

import json
from pathlib import PurePosixPath
from typing import Any, Iterable, Callable

import smart_open

from models.manifests import S3Location, SuccessManifest, FailureManifest, TransformerManifest


def _success_lines(batches_ids: list[list[str]], source_id_template: str) -> Iterable[str]:
    for ids in batches_ids:
        wrapped_ids = [source_id_template.format(i) for i in ids]
        yield json.dumps({"ids": wrapped_ids})


def _failure_lines(errors: list[dict[str, Any]]) -> Iterable[str]:
    for err in errors:
        yield json.dumps(err)


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
        source_id_template: str,
    ) -> None:
        base = f"{changeset_id or 'reindex'}.{job_id}.ids"
        self.success_filename = f"{base}.ndjson"
        self.failure_filename = f"{base}.failures.ndjson"
        self.prefix = prefix
        self.bucket = bucket
        self.source_id_template = source_id_template

    def write_success(self, batches_ids: list[list[str]]) -> S3Location:
        key = PurePosixPath(self.prefix) / self.success_filename
        uri = f"s3://{self.bucket}/{key.as_posix()}"
        with smart_open.open(uri, "w", encoding="utf-8") as f:
            for line in _success_lines(batches_ids, self.source_id_template):
                f.write(line + "\n")
        return S3Location(bucket=self.bucket, key=key.as_posix())

    def write_failures(self, errors: list[dict[str, Any]]) -> S3Location:
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
        errors: list[dict[str, Any]],
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
