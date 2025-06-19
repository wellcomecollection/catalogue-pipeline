from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from utils.aws import pydantic_from_s3_json, pydantic_to_s3_json


class PipelineReport(BaseModel):
    _report_type: str
    pipeline_date: str
    index_date: str
    job_id: str | None


class TriggerReport(PipelineReport):
    _report_type = "trigger"
    record_count: int


class LoaderReport(PipelineReport):
    _report_type = "loader"
    record_count: int
    total_file_size: int


class IndexRemoverReport(PipelineReport):
    _report_type = "index_remover"
    deleted_count: int | None
    date: str


class IndexerReport(PipelineReport):
    _report_type = "indexer"
    previous_job_id: str | None
    neptune_record_count: int
    previous_neptune_record_count: int | None
    es_record_count: int | None
    previous_es_record_count: int | None


from enum import Enum
class ReportType(str, Enum):
    trigger = TriggerReport._report_type
    loader = LoaderReport._report_type
    indexer = IndexerReport._report_type
    index_remover = IndexRemoverReport._report_type


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

def read_report_from_s3(
    report_type: ReportType,
    pipeline_date: str,
    index_date: str,
    job_id: str | None = None,
    ignore_missing: bool = False,
) -> PipelineReport | None:
    s3_url = get_report_s3_url(report_type, pipeline_date, index_date, job_id)
    
    return pydantic_from_s3_json(
        report_type, s3_url, ignore_missing=ignore_missing
    )
    
def build_indexer_report(
    current_report: TriggerReport | LoaderReport,
    latest_report: TriggerReport | LoaderReport,
) -> None:
    s3_url_current_indexer_report = get_report_s3_url(
        ReportType.indexer, 
        current_report.pipeline_date, 
        current_report.index_date, 
        current_report.job_id
    )
    
    s3_url_latest_indexer_report = get_report_s3_url(
        ReportType.indexer, 
        current_report.pipeline_date, 
        current_report.index_date, 
    )
    
    # new_indexer_report = read_report_from_s3(
    #     ReportType.indexer,
    #     current_report.pipeline_date,
    #     current_report.index_date,
    #     ignore_missing=True
    # )

    indexer_report = pydantic_from_s3_json(
        IndexerReport, s3_url_current_indexer_report, ignore_missing=True
    )
    
    # assert (new_indexer_report == indexer_report), (
    #     f"indexer_report mismatch! Expected: {new_indexer_report}, but got: {indexer_report}"
    # )
    
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
        pydantic_to_s3_json(indexer_report, s3_url_current_indexer_report)

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
        pydantic_to_s3_json(updated_indexer_report, s3_url_latest_indexer_report)
        
        # write the final indexer report to s3 as job_id
        pydantic_to_s3_json(updated_indexer_report, s3_url_current_indexer_report)
