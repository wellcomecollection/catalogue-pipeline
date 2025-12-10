from __future__ import annotations

from typing import ClassVar

from adapters.axiell.models.step_events import AxiellAdapterLoaderEvent, LoaderResponse
from models.events import IncrementalWindow
from utils.reporting import PipelineMetric, PipelineReport


class AxiellReport(PipelineReport):
    # These overrides ensure that Axiell reports do not attempt to publish to S3 by default
    # And that window is required
    window: IncrementalWindow
    publish_to_s3: bool = False

    @property
    def metric_dimensions(self) -> dict:
        return {
            "adapter_type": "axiell",
            "adapter_step": self.label,
        }


class AxiellLoaderReport(AxiellReport):
    label: ClassVar[str] = "adapter_loader"
    window_success_count: int
    window_failure_count: int = 0
    record_changes_count: int = 0
    changeset_count: int = 0

    @classmethod
    def from_loader(
        cls, event: AxiellAdapterLoaderEvent, response: LoaderResponse
    ) -> AxiellLoaderReport:
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
