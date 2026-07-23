"""Generic reporting classes for OAI-PMH adapters.

Provides base classes for adapter metrics and reports with config-driven
dimensions, enabling reuse across Axiell, FOLIO, and other adapters.
"""

from __future__ import annotations

from pathlib import PurePosixPath
from typing import ClassVar
from uuid import uuid4

from pydantic import Field

from adapters.extractors.oai_pmh.models.step_events import (
    OAIPMHIdLoaderResponse,
    OAIPMHLoaderEvent,
    OAIPMHLoaderResponse,
)
from adapters.utils.window_summary import WindowSummary
from models.incremental_window import IncrementalWindow
from utils.reporting import PipelineMetric, PipelineReport


class OAIPMHReportBase(PipelineReport):
    """Base report class for OAI-PMH adapters.

    Subclasses should set adapter_type to drive metrics dimensions.
    The pipeline_step dimension is automatically injected by PipelineReport.put_metrics().
    Carries no window, so it also serves steps that do not harvest one.
    """

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
    def metric_dimensions(self) -> dict:
        return {
            "adapter_type": self.adapter_type,
        }


class OAIPMHReport(OAIPMHReportBase):
    """Base report for window-harvesting steps, keyed on the harvested window."""

    window: IncrementalWindow

    @property
    def s3_uri(self) -> str:
        start = self.window.start_time.strftime("%Y%m%dT%H%M%S")
        end = self.window.end_time.strftime("%Y%m%dT%H%M%S")
        path = PurePosixPath(
            self.report_s3_prefix,
            "reports",
            self.adapter_type,
            self.label,
            f"{start}_{end}.json",
        )
        return f"s3://{self.report_s3_bucket}/{path}"


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


class OAIPMHIdLoadReport(OAIPMHReportBase):
    """Loader step report for id mode.

    A separate label from the window-mode loader report, so runs that harvest no
    windows do not land zeroes on the window dashboards.
    """

    label: ClassVar[str] = "adapter_id_load"
    job_id: str
    requested_count: int
    recovered_count: int
    removed_count: int
    unfetchable_count: int
    changeset_count: int = 0
    record_changes_count: int = 0

    report_id: str = Field(default_factory=lambda: uuid4().hex[:8])
    """Disambiguates the S3 key. ``job_id`` is only minute-resolution, and the
    per-run id ceiling encourages splitting a recovery across several runs, so
    two of them starting in the same minute would otherwise overwrite each
    other's report."""

    removed: list[str] = Field(default_factory=list)
    """Every id the source reported as no longer existing. These are counted but
    never written, so this is the only record of which ids vanished."""

    unfetchable: list[str] = Field(default_factory=list)
    """Every unfetchable id. The response carries only a sample, so this is the
    complete record of what needs another attempt."""

    emit_metrics: bool = True
    """Publish CloudWatch metrics. Disabled for local runs."""

    @property
    def publish_to_cloudwatch(self) -> bool:
        return self.emit_metrics

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
    def from_id_load(
        cls,
        response: OAIPMHIdLoaderResponse,
        *,
        adapter_type: str,
        removed: list[str],
        unfetchable: list[str],
        report_s3_bucket: str | None = None,
        report_s3_prefix: str = "dev",
        emit_metrics: bool = True,
    ) -> OAIPMHIdLoadReport:
        """Create a report from an id-mode response.

        Args:
            response: The id-mode loader response.
            adapter_type: Adapter identifier for metrics (e.g., 'axiell').
            removed: The full list of ids the source reported as gone.
            unfetchable: The full unfetchable id list, not the response sample.
            report_s3_bucket: S3 bucket for report storage (None to skip S3).
            report_s3_prefix: S3 key prefix for report paths.
            emit_metrics: Whether to publish CloudWatch metrics.
        """
        return cls(
            adapter_type=adapter_type,
            publish_to_s3=report_s3_bucket is not None,
            report_s3_bucket=report_s3_bucket,
            report_s3_prefix=report_s3_prefix,
            job_id=response.job_id,
            requested_count=response.requested,
            recovered_count=response.recovered,
            removed_count=response.removed,
            unfetchable_count=response.unfetchable_count,
            changeset_count=len(response.changeset_ids),
            record_changes_count=response.changed_record_count,
            removed=removed,
            unfetchable=unfetchable,
            emit_metrics=emit_metrics,
        )

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="requested_count", value=self.requested_count),
            PipelineMetric(name="recovered_count", value=self.recovered_count),
            PipelineMetric(name="removed_count", value=self.removed_count),
            PipelineMetric(name="unfetchable_count", value=self.unfetchable_count),
            PipelineMetric(
                name="record_changes_count", value=self.record_changes_count
            ),
            PipelineMetric(name="changeset_count", value=self.changeset_count),
        ]
