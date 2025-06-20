from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from utils.aws import pydantic_from_s3_json, pydantic_to_s3_json


from enum import Enum
class ReportType(str, Enum):
    trigger = "trigger"
    loader = "loader"
    indexer = "indexer"
    index_remover = "index_remover"

    def get_class(self):
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
    job_id: str | None = None
) -> str:    
    report_name = f"report.{report_type.name}.json"
    if job_id is not None:
        report_prefix = f"{pipeline_date}/{index_date}/{job_id}"
    else:
        report_prefix = f"{pipeline_date}/{index_date}"
        
    return f"s3://{INGESTOR_S3_BUCKET}/{INGESTOR_S3_PREFIX}/{report_prefix}/{report_name}"

class PipelineReport(BaseModel):
    _report_type: ReportType
    pipeline_date: str
    index_date: str
    job_id: str | None

    @classmethod
    def read(
        cls,
        pipeline_date: str,
        index_date: str,
        job_id: str | None = None,
        ignore_missing: bool = False,
    ) -> "PipelineReport | None":
        cls_report_type = cls._report_type.get_default()
        
        s3_url = get_report_s3_url(
            cls_report_type,
            pipeline_date,
            index_date,
            job_id
        )
            
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
            job_id=self.job_id if not latest else None
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

def read_report_from_s3(
    report_type: ReportType,
    pipeline_date: str,
    index_date: str,
    job_id: str | None = None,
    ignore_missing: bool = False,
) -> PipelineReport | None:
    s3_url = get_report_s3_url(report_type, pipeline_date, index_date, job_id)
    
    return pydantic_from_s3_json(
        report_type.get_class(), s3_url, ignore_missing=ignore_missing
    )

def write_report_to_s3(
    report: PipelineReport,
) -> None:
    s3_url = get_report_s3_url(
        ReportType(report._report_type), 
        report.pipeline_date,
        report.index_date,
        report.job_id
    )
    
    pydantic_to_s3_json(report, s3_url)

def build_indexer_report(
    current_report: TriggerReport | LoaderReport,
    latest_report: TriggerReport | LoaderReport,
) -> None:

    indexer_report = IndexerReport.read(
        current_report.pipeline_date,
        current_report.index_date,
        current_report.job_id,
        ignore_missing=True
    )
    
    if indexer_report is None:
        indexer_report = IndexerReport(
            pipeline_date=current_report.pipeline_date,
            index_date=current_report.index_date,
            job_id=current_report.job_id,
            previous_job_id=latest_report.job_id if latest_report else None,
            neptune_record_count=current_report.record_count,
            previous_neptune_record_count=latest_report.record_count if latest_report else None,
            es_record_count=None,
            previous_es_record_count=None,
        )
        indexer_report.write()

    else:
        updated_indexer_report = IndexerReport(
            pipeline_date=indexer_report.pipeline_date,
            index_date=indexer_report.index_date,
            job_id=indexer_report.job_id,
            previous_job_id=indexer_report.previous_job_id,
            neptune_record_count=indexer_report.neptune_record_count,
            previous_neptune_record_count=indexer_report.previous_neptune_record_count,
            es_record_count=current_report.record_count,
            previous_es_record_count=latest_report.record_count,
        )

        # write the final indexer report to s3 as latest
        updated_indexer_report.write()
        updated_indexer_report.write(latest=True)