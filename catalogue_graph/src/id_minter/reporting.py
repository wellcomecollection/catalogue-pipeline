from __future__ import annotations

import os
from collections.abc import Sequence
from pathlib import PurePosixPath
from typing import ClassVar

from pydantic import BaseModel, PrivateAttr

from utils.reporting import PipelineMetric, PipelineReport


class IdMinterReport(PipelineReport):
    label: ClassVar[str] = os.environ.get("PIPELINE_STEP", "id_minter")

    pipeline_date: str
    job_id: str
    successful_ids: list[str]
    errors: Sequence[BaseModel]

    _s3_bucket: str = PrivateAttr()
    _s3_prefix: str = PrivateAttr()

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
            PipelineMetric(name="success_count", value=len(self.successful_ids)),
            PipelineMetric(name="failure_count", value=len(self.errors)),
        ]

    @property
    def s3_uri(self) -> str:
        path = PurePosixPath(
            f"pipeline-{self.pipeline_date}", self._s3_prefix, f"{self.job_id}.ndjson"
        )
        return f"s3://{self._s3_bucket}/{path}"
