"""Generic reporting classes for OAI-PMH adapters.

Provides base classes for adapter metrics and reports with config-driven
dimensions, enabling reuse across Axiell, FOLIO, and other adapters.
"""

from __future__ import annotations

from typing import TYPE_CHECKING, ClassVar

from models.events import IncrementalWindow
from utils.reporting import PipelineMetric, PipelineReport

if TYPE_CHECKING:
    from adapters.oai_pmh.models.step_events import (
        OAIPMHLoaderEvent,
        OAIPMHLoaderResponse,
    )


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

    @property
    def metric_namespace(self) -> str:
        return "catalogue_adapters"

    @property
    def metric_dimensions(self) -> dict:
        return {
            "adapter_type": self.adapter_type,
            "adapter_step": self.label,
        }


class OAIPMHLoaderReport(OAIPMHReport):
    """Loader step report for OAI-PMH adapters."""

    label: ClassVar[str] = "adapter_loader"
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
    ) -> OAIPMHLoaderReport:
        """Create a report from loader event and response.

        Args:
            event: The loader request event.
            response: The loader response with window summaries.
            adapter_type: Adapter identifier for metrics (e.g., 'axiell').
        """
        window_success_count = sum(
            1 for summary in response.summaries if summary.state == "success"
        )
        window_failure_count = len(response.summaries) - window_success_count
        return cls(
            window=event.window,
            adapter_type=adapter_type,
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
