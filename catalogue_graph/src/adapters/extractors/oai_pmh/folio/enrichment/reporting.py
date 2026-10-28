"""Report for the FOLIO enrichment step, carrying the records it left out."""

from __future__ import annotations

from pathlib import PurePosixPath
from typing import ClassVar
from uuid import uuid4

from pydantic import Field

from adapters.extractors.oai_pmh.folio.enrichment.models import RejectedInventoryRecord
from adapters.extractors.oai_pmh.reporting import OAIPMHReportBase
from utils.reporting import PipelineMetric


class FolioEnrichmentReport(OAIPMHReportBase):
    """Counts for the alarm, and the full rejected list for whoever fixes them."""

    label: ClassVar[str] = "adapter_enrichment"
    job_id: str
    requested_count: int
    rejected: list[RejectedInventoryRecord] = Field(default_factory=list)
    failed: bool = False
    """True when the step failed because the rejections looked systemic."""

    report_id: str = Field(default_factory=lambda: uuid4().hex[:8])
    """Disambiguates the S3 key, since ``job_id`` is only minute-resolution."""

    emit_metrics: bool = True
    """Publish CloudWatch metrics. Disabled for local runs."""

    @property
    def publish_to_cloudwatch(self) -> bool:
        return self.emit_metrics

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="requested_count", value=self.requested_count),
            PipelineMetric(name="rejected_record_count", value=len(self.rejected)),
        ]

    @property
    def s3_uri(self) -> str:
        path = PurePosixPath(
            self.report_s3_prefix,
            "reports",
            self.adapter_type,
            self.label,
            f"{self.job_id}_{self.report_id}.json",
        )
        return f"s3://{self.report_s3_bucket}/{path}"

    @classmethod
    def build(
        cls,
        *,
        adapter_type: str,
        job_id: str,
        requested_count: int,
        rejected: list[RejectedInventoryRecord],
        failed: bool = False,
        report_s3_bucket: str | None = None,
        report_s3_prefix: str = "dev",
        emit_metrics: bool = True,
    ) -> FolioEnrichmentReport:
        """Only runs that rejected something are written to S3; every run emits metrics."""
        return cls(
            adapter_type=adapter_type,
            job_id=job_id,
            requested_count=requested_count,
            rejected=rejected,
            failed=failed,
            publish_to_s3=report_s3_bucket is not None and bool(rejected),
            report_s3_bucket=report_s3_bucket,
            report_s3_prefix=report_s3_prefix,
            emit_metrics=emit_metrics,
        )
