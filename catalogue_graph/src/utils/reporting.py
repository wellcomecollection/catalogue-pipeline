from typing import ClassVar, Self

from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from utils.aws import pydantic_from_s3_json, pydantic_to_s3_json


class PipelineReport(BaseModel):
    label: ClassVar[str]
    pipeline_date: str
    index_date: str
    job_id: str | None

    @staticmethod
    def _get_report_s3_url(
        report_type: type["PipelineReport"],
        pipeline_date: str,
        index_date: str,
        job_id: str | None = None,
    ) -> str:
        report_name = f"report.{report_type.label}.json"
        if job_id is not None:
            report_prefix = f"{pipeline_date}/{index_date}/{job_id}"
        else:
            report_prefix = f"{pipeline_date}/{index_date}"

        return f"s3://{INGESTOR_S3_BUCKET}/{INGESTOR_S3_PREFIX}/{report_prefix}/{report_name}"

    @classmethod
    def read(
        cls,
        pipeline_date: str,
        index_date: str,
        job_id: str | None = None,
        ignore_missing: bool = False,
    ) -> Self | None:
        s3_url = PipelineReport._get_report_s3_url(
            cls, pipeline_date, index_date, job_id
        )

        return pydantic_from_s3_json(cls, s3_url, ignore_missing=ignore_missing)

    def write(
        self,
        latest: bool = False,
    ) -> None:
        s3_url = PipelineReport._get_report_s3_url(
            self.__class__,
            self.pipeline_date,
            self.index_date,
            job_id=self.job_id if not latest else None,
        )

        pydantic_to_s3_json(self, s3_url)


class TriggerReport(PipelineReport):
    label: ClassVar[str] = "trigger"
    record_count: int


class LoaderReport(PipelineReport):
    label: ClassVar[str] = "loader"
    record_count: int
    total_file_size: int


class DeletionReport(PipelineReport):
    label: ClassVar[str] = "deletions"
    deleted_count: int | None
    date: str


class IndexerReport(PipelineReport):
    label: ClassVar[str] = "indexer"
    success_count: int | None = None
    previous_job_id: str | None = None
