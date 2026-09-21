from pathlib import PurePosixPath

from pydantic import BaseModel

import config
from models.events import BasePipelineEvent


class MergerEvent(BasePipelineEvent):
    job_id: str
    iceberg_snapshot_id: int | None = None

    @property
    def s3_service_prefix_parts(self) -> list[str]:
        return [config.MERGER_S3_PREFIX]

    @property
    def components_s3_uri(self) -> str:
        """The components emitted by the previous run, shared by every run of a pipeline."""
        parts = [
            f"graph-{self.graph_date or 'prod'}",
            f"pipeline-{self.pipeline_date}",
            config.MERGER_S3_PREFIX,
            "components.parquet",
        ]
        return f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{PurePosixPath(*parts)}"

    def get_s3_uri(self, file_name: str) -> str:
        parts = [*self.s3_prefix_parts, f"job-{self.job_id}", f"{file_name}.parquet"]
        return f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{PurePosixPath(*parts)}"


class MergerResult(BaseModel):
    job_id: str
    iceberg_snapshot_id: int
    work_count: int
    changed_work_count: int
    changed_component_count: int
    changed_components_s3_uri: str
