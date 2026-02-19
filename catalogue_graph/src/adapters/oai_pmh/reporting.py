"""Generic reporting classes for OAI-PMH adapters.

Provides base classes for adapter metrics and reports with config-driven
dimensions, enabling reuse across Axiell, FOLIO, and other adapters.
"""

from __future__ import annotations

from typing import ClassVar

from pydantic import Field

from adapters.oai_pmh.models.step_events import (
    OAIPMHLoaderEvent,
    OAIPMHLoaderResponse,
)
from adapters.utils.window_summary import WindowSummary
from models.events import IncrementalWindow
from utils.reporting import PipelineMetric, PipelineReport


class OAIPMHReport(PipelineReport):
    """Base report class for OAI-PMH adapters.

    Subclasses should set adapter_type and adapter_step to drive metrics dimensions.
    Window is required for adapter reports.
    """

    window: IncrementalWindow
    adapter_type: str
    """Adapter identifier (e.g., 'axiell', 'folio') for metrics dimensions."""

    publish_to_s3: bool = False
    """OAI-PMH adapter reports do not publish to S3 by default."""

    report_s3_bucket: str | None = None
    """S3 bucket for report storage."""

    report_s3_prefix: str = "dev"
    """S3 key prefix for report paths."""

    @property
    def metric_namespace(self) -> str:
        return "catalogue_adapters"

    @property
    def s3_uri(self) -> str:
        start = self.window.start_time.strftime("%Y%m%dT%H%M%S")
        end = self.window.end_time.strftime("%Y%m%dT%H%M%S")
        return (
            f"s3://{self.report_s3_bucket}/{self.report_s3_prefix}"
            f"/reports/{self.adapter_type}/{self.label}/{start}_{end}.json"
        )

    @property
    def metric_dimensions(self) -> dict:
        return {
            "adapter_type": self.adapter_type,
            "adapter_step": self.label,
        }


class OAIPMHLoaderReport(OAIPMHReport):
    """Loader step report for OAI-PMH adapters."""

    label: ClassVar[str] = "adapter_loader"
    summaries: list[WindowSummary] = Field(default_factory=list)
    window_success_count: int
    window_failure_count: int = 0
    record_changes_count: int = 0
    changeset_count: int = 0

    @classmethod
    def from_loader(
        cls,
        event: OAIPMHLoaderEvent,
        response: OAIPMHLoaderResponse,
        *,
        adapter_type: str,
        report_s3_bucket: str | None = None,
        report_s3_prefix: str = "dev",
    ) -> OAIPMHLoaderReport:
        """Create a report from loader event and response.

        Args:
            event: The loader request event.
            response: The loader response with window summaries.
            adapter_type: Adapter identifier for metrics (e.g., 'axiell').
            report_s3_bucket: S3 bucket for report storage (None to skip S3).
            report_s3_prefix: S3 key prefix for report paths.
        """
        window_success_count = sum(
            1 for summary in response.summaries if summary.state == "success"
        )
        window_failure_count = len(response.summaries) - window_success_count
        return cls(
            window=event.window,
            adapter_type=adapter_type,
            publish_to_s3=report_s3_bucket is not None,
            report_s3_bucket=report_s3_bucket,
            report_s3_prefix=report_s3_prefix,
            summaries=response.summaries,
            window_success_count=window_success_count,
            window_failure_count=window_failure_count,
            record_changes_count=response.changed_record_count,
            changeset_count=len(response.changeset_ids),
        )

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(
                name="window_success_count", value=self.window_success_count
            ),
            PipelineMetric(
                name="window_failure_count", value=self.window_failure_count
            ),
            PipelineMetric(
                name="record_changes_count", value=self.record_changes_count
            ),
            PipelineMetric(name="changeset_count", value=self.changeset_count),
        ]
