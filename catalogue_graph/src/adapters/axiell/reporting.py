"""Axiell adapter reporting.

Re-exports from the generic OAI-PMH reporting module for backwards compatibility.
New code should use the generic classes from adapters.oai_pmh.reporting directly.
"""

from __future__ import annotations

from typing import TYPE_CHECKING, ClassVar

from adapters.oai_pmh.reporting import OAIPMHLoaderReport, OAIPMHReport
from utils.reporting import PipelineMetric

if TYPE_CHECKING:
    from adapters.axiell.models.step_events import (
        AxiellAdapterLoaderEvent,
        LoaderResponse,
    )


class AxiellReport(OAIPMHReport):
    """Axiell-specific report base class.

    Deprecated: Use OAIPMHReport directly with adapter_type='axiell'.
    """

    adapter_type: str = "axiell"


class AxiellLoaderReport(AxiellReport):
    """Loader step report for the Axiell adapter.

    Deprecated: Use OAIPMHLoaderReport directly with adapter_type='axiell'.
    """

    label: ClassVar[str] = "adapter_loader"
    window_success_count: int
    window_failure_count: int = 0
    record_changes_count: int = 0
    changeset_count: int = 0

    @classmethod
    def from_loader(
        cls, event: AxiellAdapterLoaderEvent, response: LoaderResponse
    ) -> AxiellLoaderReport:
        """Create a report from loader event and response."""
        window_success_count = sum(
            1 for summary in response.summaries if summary.state == "success"
        )
        window_failure_count = len(response.summaries) - window_success_count
        return cls(
            window=event.window,
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


__all__ = [
    "AxiellReport",
    "AxiellLoaderReport",
    # Re-export generic classes
    "OAIPMHReport",
    "OAIPMHLoaderReport",
]
