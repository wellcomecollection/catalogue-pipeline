"""Transformer-specific manifest writer.

Subclasses the shared ManifestWriter, adding changeset_ids and returning
a TransformerManifest.
"""

from __future__ import annotations

from collections.abc import Sequence

from pydantic import BaseModel

from utils.manifests import BatchLine, ManifestWriter
from utils.models.manifests import StepManifest


class SourceIdentifierBatchLine(BatchLine):
    """Batch line for the adapter transformer — carries source identifiers."""

    sourceIdentifiers: list[str]


class TransformerManifest(StepManifest):
    """Manifest specific to the adapter transformer step."""

    changeset_ids: list[str]


class TransformerManifestWriter(ManifestWriter):
    """ManifestWriter that produces a TransformerManifest with changeset_ids."""

    def __init__(
        self,
        job_id: str,
        changeset_ids: list[str],
        *,
        bucket: str,
        prefix: str,
    ) -> None:
        label = "||".join(changeset_ids) or "reindex"
        super().__init__(job_id=job_id, label=label, bucket=bucket, prefix=prefix)
        self.changeset_ids = changeset_ids

    def _make_batch_line(self, ids: list[str]) -> SourceIdentifierBatchLine:
        return SourceIdentifierBatchLine(sourceIdentifiers=ids, jobId=self.job_id)

    def build_manifest(
        self,
        *,
        successful_ids: list[str],
        errors: Sequence[BaseModel],
    ) -> TransformerManifest:
        step_manifest = super().build_manifest(
            successful_ids=successful_ids, errors=errors
        )
        return TransformerManifest(
            changeset_ids=self.changeset_ids,
            job_id=step_manifest.job_id,
            successes=step_manifest.successes,
            failures=step_manifest.failures,
        )
