from pathlib import PurePosixPath
from typing import Annotated

from pydantic import BaseModel, PlainSerializer

import config
from models.events import BasePipelineEvent

# Written as strings: Step Functions' JSONata reads numbers as doubles, which round
# 64-bit Iceberg snapshot ids.
SnapshotId = Annotated[int, PlainSerializer(str, return_type=str)]


class MatchEvent(BasePipelineEvent):
    job_id: str
    iceberg_snapshot_id: SnapshotId | None = None

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

    def failed_components_s3_uri(self, group: int) -> str:
        return self.get_s3_uri(f"failed_components-{group}")


class MergeGroupEvent(MatchEvent):
    """One group of a run's changed components, merged in its own invocation."""

    iceberg_snapshot_id: SnapshotId
    group: int


class CommitEvent(MatchEvent):
    """Replaces the baseline once every group of a run is merged."""

    group_count: int


class MatchResult(BaseModel):
    job_id: str
    iceberg_snapshot_id: SnapshotId | None
    work_count: int
    changed_work_count: int
    changed_component_count: int
    # The changed components are merged in groups numbered from 0.
    group_count: int
    changed_components_s3_uri: str


class MergeGroupResult(BaseModel):
    group: int
    component_count: int
    # Documents Elasticsearch accepted, and those it rejected because a run at a later
    # snapshot had already written them.
    work_count: int
    image_count: int
    superseded_count: int
    # Components that could not be read, merged or indexed; the next run retries them.
    failed_component_count: int


class CommitResult(BaseModel):
    failed_component_count: int
