from datetime import datetime
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
        """The components of the last run whose merged works were all written."""
        parts = [
            f"graph-{self.graph_date}",
            f"pipeline-{self.pipeline_date}",
            config.MERGER_S3_PREFIX,
            "components.parquet",
        ]
        return f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{PurePosixPath(*parts)}"

    def get_s3_uri(self, file_name: str) -> str:
        parts = [*self.s3_prefix_parts, f"job-{self.job_id}", f"{file_name}.parquet"]
        return f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{PurePosixPath(*parts)}"


class MergeGroupEvent(MergerEvent):
    """One group of a run's changed components, merged in its own invocation."""

    iceberg_snapshot_id: int
    group: int
    # The snapshot's commit time, so a run never overwrites one at a later snapshot.
    merged_time: datetime


class MergerResult(BaseModel):
    job_id: str
    iceberg_snapshot_id: int | None
    work_count: int
    changed_work_count: int
    changed_component_count: int
    changed_components_s3_uri: str
    # This run's components, which replace the baseline once every group is merged.
    job_components_s3_uri: str
    components_s3_uri: str
    merge_groups: list[MergeGroupEvent]


class MergeGroupResult(BaseModel):
    group: int
    component_count: int
    work_count: int
    merged_work_count: int
    image_count: int
