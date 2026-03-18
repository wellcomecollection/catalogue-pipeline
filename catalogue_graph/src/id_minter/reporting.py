from __future__ import annotations

from typing import ClassVar

from utils.reporting import PipelineMetric, PipelineReport


class IdMinterReport(PipelineReport):
    label: ClassVar[str] = "id_minter"

    pipeline_date: str
    success_count: int
    failure_count: int
    publish_to_s3: bool = False

    @property
    def publish_to_cloudwatch(self) -> bool:
        return self.pipeline_date != "dev"

    @property
    def metric_namespace(self) -> str:
        return "catalogue_graph_pipeline"

    @property
    def metric_dimensions(self) -> dict:
        return {"pipeline_date": self.pipeline_date}

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="success_count", value=self.success_count),
            PipelineMetric(name="failure_count", value=self.failure_count),
        ]
