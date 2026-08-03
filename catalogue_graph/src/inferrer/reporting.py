from typing import ClassVar

from pydantic import Field

from utils.reporting import PipelineMetric, PipelineReport


class InferenceReport(PipelineReport):
    """CloudWatch metrics for an inference task; download_failure_count is alarmed."""

    label: ClassVar[str] = "inference_manager"

    pipeline_date: str
    augmented_count: int
    download_failure_count: int

    # Metrics only; per-task S3 reports would be noise at partition granularity.
    publish_to_s3: bool = Field(default=False, exclude=True)

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
            PipelineMetric(name="augmented_count", value=self.augmented_count),
            PipelineMetric(
                name="download_failure_count", value=self.download_failure_count
            ),
        ]
