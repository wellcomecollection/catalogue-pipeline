"""Utilities for writing transformer output manifests (success & failure)."""

from __future__ import annotations

from collections.abc import Iterable
from itertools import batched
from pathlib import PurePosixPath

import smart_open

from adapters.transformers.base_transformer import TransformationError
from adapters.transformers.models.manifests import (
    FailureManifest,
    S3Location,
    SuccessBatchLine,
    SuccessManifest,
    TransformerManifest,
)

# Batch size for converting Arrow tables to Python objects before indexing
# This must result in batches of output ids that fit in the 256kb item
# limit for step function invocations (with some margin).
BATCH_SIZE = 5_000


def _success_lines(successful_ids: set[str], job_id: str) -> Iterable[str]:
    for batch in batched(successful_ids, BATCH_SIZE):
        line = SuccessBatchLine(sourceIdentifiers=list(batch), jobId=job_id)
        yield line.model_dump_json()


class ManifestWriter:
    """Write success & failure manifests for the transformer.

    Success manifest: <changeset|reindex>.<job>.ids.ndjson
    Failure manifest: <changeset|reindex>.<job>.ids.failures.ndjson (only if failures)
    """

    def __init__(
        self,
        job_id: str,
        changeset_ids: list[str],
        *,
        bucket: str,
        prefix: str,
    ) -> None:
        base = f"{'||'.join(changeset_ids) or 'reindex'}.{job_id}.ids"
        self.success_filename = f"{base}.ndjson"
        self.failure_filename = f"{base}.failures.ndjson"
        self.prefix = prefix
        self.bucket = bucket
        self.job_id = job_id
        self.changeset_ids = changeset_ids

    def _write_lines(self, lines: Iterable[str], file_name: str) -> S3Location:
        key = PurePosixPath(self.prefix) / file_name
        uri = f"s3://{self.bucket}/{key.as_posix()}"
        with smart_open.open(uri, "w", encoding="utf-8") as f:
            for line in lines:
                f.write(line + "\n")

        return S3Location(bucket=self.bucket, key=key.as_posix())

    def write_success(self, successful_ids: set[str]) -> S3Location:
        return self._write_lines(
            _success_lines(successful_ids, self.job_id), self.success_filename
        )

    def write_failures(self, errors: list[TransformationError]) -> S3Location:
        lines = (e.model_dump_json() for e in errors)
        return self._write_lines(lines, self.failure_filename)

    def build_manifest(
        self,
        *,
        successful_ids: set[str],
        errors: list[TransformationError],
    ) -> TransformerManifest:
        success_loc = self.write_success(successful_ids)
        failures_section: FailureManifest | None = None
        if errors:
            failure_loc = self.write_failures(errors)
            failures_section = FailureManifest(
                count=len(errors), error_file_location=failure_loc
            )
        return TransformerManifest(
            changeset_ids=self.changeset_ids,
            job_id=self.job_id,
            successes=SuccessManifest(
                count=len(successful_ids), batch_file_location=success_loc
            ),
            failures=failures_section,
        )
