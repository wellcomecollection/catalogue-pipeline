"""Run report for the Axiell to Folio sync step.

Publishes per-run operation counts to CloudWatch under the
``catalogue_adapters`` namespace with an ``adapter_type`` dimension,
following the same convention as the other adapter reports, and writes
the full run report (counts plus per-record outcomes) to S3 as a single
JSON document via the shared ``PipelineReport`` machinery.

CloudWatch metrics are suppressed when ``dry_run=True`` (no real writes
happened); the S3 report is still written so dry runs can be validated.
"""

from __future__ import annotations

from typing import ClassVar

from pydantic import Field

from adapters.steps.axiell_folio_sync.models import (
    SyncDeletionEntry,
    SyncErrorEntry,
    SyncSuccessEntry,
)
from utils.reporting import PipelineMetric, PipelineReport

PIPELINE_STEP = "axiell_folio_sync"


class AxiellFolioSyncReport(PipelineReport):
    label: ClassVar[str] = PIPELINE_STEP
    job_id: str
    changeset_ids: list[str] = Field(default_factory=list)
    dry_run: bool
    counts: dict[str, int]
    successful: list[SyncSuccessEntry] = Field(default_factory=list)
    errors: list[SyncErrorEntry] = Field(default_factory=list)
    deletions: list[SyncDeletionEntry] = Field(default_factory=list)
    s3_bucket: str | None = Field(default=None, exclude=True)

    @property
    def publish_to_cloudwatch(self) -> bool:
        return not self.dry_run

    @property
    def metric_namespace(self) -> str:
        return "catalogue_adapters"

    @property
    def metric_dimensions(self) -> dict[str, str]:
        return {"adapter_type": "axiell_folio_sync"}

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="records_created", value=self.counts.get("created", 0)),
            PipelineMetric(name="records_updated", value=self.counts.get("updated", 0)),
            PipelineMetric(
                name="records_suppressed", value=self.counts.get("suppressed", 0)
            ),
            PipelineMetric(
                name="deletions_processed", value=self.counts.get("deletions", 0)
            ),
            PipelineMetric(name="records_skipped", value=self.counts.get("skipped", 0)),
            PipelineMetric(
                name="records_tombstoned", value=self.counts.get("tombstone", 0)
            ),
            PipelineMetric(name="records_failed", value=self.counts.get("failed", 0)),
            PipelineMetric(name="records_processed", value=self.counts.get("total", 0)),
        ]

    @property
    def s3_uri(self) -> str:
        if self.s3_bucket is None:
            raise ValueError("No S3 bucket configured for the sync report")
        # The manifests/ prefix is load-bearing: the bucket's Terraform lifecycle
        # rule expires objects under it after manifest_retention_days.
        return f"s3://{self.s3_bucket}/manifests/{self.job_id}.json"
