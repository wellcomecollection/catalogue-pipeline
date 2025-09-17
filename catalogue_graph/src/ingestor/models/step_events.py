from pathlib import PurePosixPath

import config
from models.events import BasePipelineEvent
from utils.types import IngestorLoadFormat, IngestorType


class IngestorStepEvent(BasePipelineEvent):
    ingestor_type: IngestorType
    pipeline_date: str
    index_date: str
    job_id: str
    load_format: IngestorLoadFormat = "parquet"

    def get_path_prefix(self) -> str:
        parts: list[str] = [
            config.CATALOGUE_GRAPH_S3_BUCKET,
            f"{config.INGESTOR_S3_PREFIX}_{self.ingestor_type}",
            self.pipeline_date,
            self.index_date,
            self.job_id,
        ]
        return str(PurePosixPath(*parts))

    def get_s3_uri(self, file_name: str) -> str:
        prefix = self.get_path_prefix()
        return f"s3://{prefix}/{file_name}.{self.load_format}"


class IngestorLoaderLambdaEvent(IngestorStepEvent):
    pass


class IngestorIndexerLambdaEvent(IngestorStepEvent):
    pass


class IngestorMonitorStepEvent(IngestorStepEvent):
    force_pass: bool = False
    report_results: bool = True


class IngestorIndexerMonitorLambdaEvent(IngestorMonitorStepEvent):
    success_count: int


class IngestorLoaderMonitorLambdaEvent(IngestorMonitorStepEvent):
    events: list[IngestorIndexerLambdaEvent]
