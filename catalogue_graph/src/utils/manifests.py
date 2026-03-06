"""Shared utilities for writing step output manifests to S3.

Produces NDJSON success/failure files consumed by downstream Step Function
Map states.  Used by the adapter transformer and the ID minter.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from itertools import batched
from pathlib import PurePosixPath

import smart_open
from pydantic import BaseModel

from utils.models.manifests import (
    BatchLine,  # noqa: F401 — re-export for subclasses
    FailureManifest,
    S3Location,
    StepManifest,
    SuccessManifest,
)

# Batch size for converting Arrow tables to Python objects before indexing
# This must result in batches of output ids that fit in the 256kb item
# limit for step function invocations (with some margin).
BATCH_SIZE = 5_000


class ManifestWriter:
    """Write success & failure manifests for a pipeline step.

    Success manifest: <label>.<job>.ids.ndjson
    Failure manifest: <label>.<job>.ids.failures.ndjson (only if failures)

    Subclasses must implement ``_make_batch_line`` to define the schema of
    each NDJSON line in the success file.
    """

    def __init__(
        self,
        job_id: str,
        label: str,
        *,
        bucket: str,
        prefix: str,
    ) -> None:
        base = f"{label}.{job_id}.ids"
        self.success_filename = f"{base}.ndjson"
        self.failure_filename = f"{base}.failures.ndjson"
        self.prefix = prefix
        self.bucket = bucket
        self.job_id = job_id

    def _make_batch_line(self, ids: list[str]) -> BatchLine:
        """Create a typed batch line for the given IDs.

        Subclasses must override this to define the schema of each NDJSON line.
        """
        raise NotImplementedError("Subclasses must implement _make_batch_line")

    def _success_lines(self, successful_ids: list[str]) -> Iterable[str]:
        for batch in batched(successful_ids, BATCH_SIZE):
            line = self._make_batch_line(list(batch))
            yield line.model_dump_json()

    def _write_lines(self, lines: Iterable[str], file_name: str) -> S3Location:
        key = PurePosixPath(self.prefix) / file_name
        uri = f"s3://{self.bucket}/{key.as_posix()}"
        with smart_open.open(uri, "w", encoding="utf-8") as f:
            for line in lines:
                f.write(line + "\n")

        return S3Location(bucket=self.bucket, key=key.as_posix())

    def write_success(self, successful_ids: list[str]) -> S3Location:
        return self._write_lines(
            self._success_lines(successful_ids), self.success_filename
        )

    def write_failures(self, errors: Sequence[BaseModel]) -> S3Location:
        lines = (e.model_dump_json() for e in errors)
        return self._write_lines(lines, self.failure_filename)

    def build_manifest(
        self,
        *,
        successful_ids: list[str],
        errors: Sequence[BaseModel],
    ) -> StepManifest:
        success_loc = self.write_success(successful_ids)
        failures_section: FailureManifest | None = None
        if errors:
            failure_loc = self.write_failures(errors)
            failures_section = FailureManifest(
                count=len(errors), error_file_location=failure_loc
            )
        return StepManifest(
            job_id=self.job_id,
            successes=SuccessManifest(
                count=len(successful_ids), batch_file_location=success_loc
            ),
            failures=failures_section,
        )
