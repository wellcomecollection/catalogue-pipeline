from __future__ import annotations

from datetime import UTC, datetime
from typing import ClassVar

from adapters.ebsco.models.step_events import (
    EbscoAdapterLoaderEvent,
    LoaderResponse,
)
from models.incremental_window import IncrementalWindow
from utils.reporting import PipelineMetric, PipelineReport


class EbscoReport(PipelineReport):
    # EBSCO is not windowed; we still need a window for metric timestamps.
    # We set start and end to "now" — only end_time is used when publishing metrics.
    publish_to_s3: bool = False

    @property
    def metric_namespace(self) -> str:
        return "catalogue_adapters"

    @property
    def metric_dimensions(self) -> dict:
        return {
            "adapter_type": "ebsco",
            "adapter_step": self.label,
        }


class EbscoLoaderReport(EbscoReport):
    label: ClassVar[str] = "adapter_loader"
    changeset_count: int
    record_changes_count: int

    @classmethod
    def from_loader(
        cls,
        event: EbscoAdapterLoaderEvent,
        response: LoaderResponse,
    ) -> EbscoLoaderReport:
        now = datetime.now(UTC)
        window = IncrementalWindow(start_time=now, end_time=now)
        changeset_count = len(response.changeset_ids)

        return cls(
            window=window,
            changeset_count=changeset_count,
            record_changes_count=response.changed_record_count,
        )

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="changeset_count", value=self.changeset_count),
            PipelineMetric(
                name="record_changes_count", value=self.record_changes_count
            ),
        ]
