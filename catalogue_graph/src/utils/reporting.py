from enum import Enum
from typing import TypeVar

from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from utils.aws import pydantic_from_s3_json, pydantic_to_s3_json


class ReportType(str, Enum):
    trigger = "trigger"
    loader = "loader"
    indexer = "indexer"
    index_remover = "index_remover"

    def get_class(self):  # type: ignore[no-untyped-def]
        return {
            ReportType.trigger: TriggerReport,
            ReportType.loader: LoaderReport,
            ReportType.indexer: IndexerReport,
            ReportType.index_remover: IndexRemoverReport,
        }[self]


def get_report_s3_url(
    report_type: ReportType,
    pipeline_date: str,
    index_date: str,
    job_id: str | None = None,
) -> str:
    report_name = f"report.{report_type.name}.json"
    if job_id is not None:
        report_prefix = f"{pipeline_date}/{index_date}/{job_id}"
    else:
        report_prefix = f"{pipeline_date}/{index_date}"

    return (
        f"s3://{INGESTOR_S3_BUCKET}/{INGESTOR_S3_PREFIX}/{report_prefix}/{report_name}"
    )


PipelineReportType = TypeVar("PipelineReportType", bound="PipelineReport")


class PipelineReport(BaseModel):
    _report_type: ReportType
    pipeline_date: str
    index_date: str
    job_id: str | None

    @classmethod
    def read[PipelineReportType](
        cls,
        pipeline_date: str,
        index_date: str,
        job_id: str | None = None,
        ignore_missing: bool = False,
    ) -> PipelineReportType | None:
        cls_report_type = cls._report_type.get_default()  # type: ignore[attr-defined]

        s3_url = get_report_s3_url(cls_report_type, pipeline_date, index_date, job_id)

        return pydantic_from_s3_json(
            cls_report_type.get_class(), s3_url, ignore_missing=ignore_missing
        )

    def write(
        self,
        latest: bool = False,
    ) -> None:
        s3_url = get_report_s3_url(
            ReportType(self._report_type),
            self.pipeline_date,
            self.index_date,
            job_id=self.job_id if not latest else None,
        )

        pydantic_to_s3_json(self, s3_url)


class TriggerReport(PipelineReport):
    _report_type = ReportType.trigger
    record_count: int


class LoaderReport(PipelineReport):
    _report_type = ReportType.loader
    record_count: int
    total_file_size: int


class IndexRemoverReport(PipelineReport):
    _report_type = ReportType.index_remover
    deleted_count: int | None
    date: str


class IndexerReport(PipelineReport):
    _report_type = ReportType.indexer
    previous_job_id: str | None
    neptune_record_count: int
    previous_neptune_record_count: int | None
    es_record_count: int | None
    previous_es_record_count: int | None
