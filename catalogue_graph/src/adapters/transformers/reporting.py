from __future__ import annotations

from pathlib import PurePosixPath
from typing import ClassVar

from pydantic import Field

from core.transformer import TransformationError
from utils.reporting import PipelineMetric, PipelineReport


class TransformerReport(PipelineReport):
    label: ClassVar[str] = "adapter_transformer"

    pipeline_date: str
    transformer_type: str
    changeset_ids: list[str]
    ids: list[str]
    job_id: str
    snapshot_id: int | None = None

    successful_ids: list[str]
    errors: list[TransformationError]
    unmatched_ids: list[str]

    s3_bucket: str = Field(exclude=True)
    s3_prefix: str = Field(exclude=True)

    @property
    def publish_to_cloudwatch(self) -> bool:
        return self.pipeline_date != "dev"

    @property
    def metric_namespace(self) -> str:
        return "catalogue_graph_pipeline"

    @property
    def metric_dimensions(self) -> dict:
        return {
            "pipeline_date": self.pipeline_date,
            "transformer_type": self.transformer_type,
        }

    @property
    def metrics(self) -> list[PipelineMetric]:
        return [
            PipelineMetric(name="success_count", value=len(self.successful_ids)),
            PipelineMetric(name="failure_count", value=len(self.errors)),
        ]

    @property
    def s3_uri(self) -> str:
        run_label = "idload" if self.ids else "_".join(self.changeset_ids) or "reindex"
        file_name = f"{run_label}__{self.job_id}.json"
        path = PurePosixPath(
            f"pipeline-{self.pipeline_date}",
            self.transformer_type,
            self.s3_prefix,
            file_name,
        )
        return f"s3://{self.s3_bucket}/{path}"
