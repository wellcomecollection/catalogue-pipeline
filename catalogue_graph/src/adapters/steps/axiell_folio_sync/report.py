"""CloudWatch metrics report for the Axiell → FOLIO sync step.

Publishes per-run operation counts to CloudWatch under the
``catalogue_adapters`` namespace with an ``adapter_type`` dimension,
following the same convention as the other adapter reports.
Suppressed when ``dry_run=True`` (no real writes happened).
"""

from __future__ import annotations

from typing import ClassVar

from utils.reporting import PipelineMetric, PipelineReport

PIPELINE_STEP = "axiell_folio_sync"


class AxiellFolioSyncReport(PipelineReport):
    label: ClassVar[str] = PIPELINE_STEP
    dry_run: bool
    counts: dict[str, int]
    publish_to_s3: bool = False

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
            PipelineMetric(name="records_skipped", value=self.counts.get("skipped", 0)),
            PipelineMetric(
                name="records_tombstoned", value=self.counts.get("tombstone", 0)
            ),
            PipelineMetric(name="records_failed", value=self.counts.get("failed", 0)),
            PipelineMetric(
                name="records_processed", value=self.counts.get("total", 0)
            ),
        ]

    @property
    def s3_uri(self) -> str:
        raise NotImplementedError("AxiellFolioSyncReport does not publish to S3")
