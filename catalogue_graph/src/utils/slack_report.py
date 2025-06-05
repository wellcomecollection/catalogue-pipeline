import boto3
import typing
import requests
from pydantic import BaseModel

from utils.aws import pydantic_from_s3_json, pydantic_to_s3_json, get_secret
from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX

class Config(BaseModel):
    ingestor_s3_bucket: str = INGESTOR_S3_BUCKET
    ingestor_s3_prefix: str = INGESTOR_S3_PREFIX
    percentage_threshold: float = 0.1

    is_local: bool = False

class TriggerReport(BaseModel):
    record_count: int
    pipeline_date: str
    index_date: str
    job_id: str

class LoaderReport(BaseModel):
    pipeline_date: str
    index_date: str
    job_id: str
    record_count: int
    total_file_size: int

class IndexRemoverReport(BaseModel):
    pipeline_date: str
    index_date: str
    deleted_count: int | None
    date: str
    
class IndexerReport(BaseModel):
    pipeline_date: str
    job_id: str
    previous_job_id: str
    neptune_record_count: int
    previous_neptune_record_count: int
    es_record_count: int | None
    previous_es_record_count: int | None

def build_indexer_report(
    current_report: TriggerReport | LoaderReport,
    latest_report: TriggerReport | LoaderReport,
    config: Config,
) -> None:
    report_name = "report.indexer.json"
    s3_url_indexer_report = f"s3://{config.ingestor_s3_bucket}/{config.ingestor_s3_prefix}/{current_report.pipeline_date}/{current_report.job_id}/{report_name}"

    indexer_report = pydantic_from_s3_json(
      IndexerReport, s3_url_indexer_report, ignore_missing=True
    )

    if indexer_report is None:
        indexer_report = IndexerReport(
            pipeline_date=current_report.pipeline_date,
            job_id=current_report.job_id,
            previous_job_id=latest_report.job_id,
            neptune_record_count=current_report.record_count,
            previous_neptune_record_count=latest_report.record_count,
            es_record_count=None,
            previous_es_record_count=None,
        )
        pydantic_to_s3_json(indexer_report, s3_url_indexer_report)

    else:
        updated_indexer_report = IndexerReport(
            pipeline_date=indexer_report.pipeline_date,
            job_id=indexer_report.job_id,
            previous_job_id=indexer_report.previous_job_id,
            neptune_record_count=indexer_report.neptune_record_count,
            previous_neptune_record_count=indexer_report.previous_neptune_record_count,
            es_record_count=current_report.record_count,
            previous_es_record_count=latest_report.record_count,
        )
        pydantic_to_s3_json(updated_indexer_report, s3_url_indexer_report)

def publish_report(report: list[typing.Any], slack_secret: str) -> None:
    slack_endpoint = get_secret(slack_secret)

    requests.post(slack_endpoint, json={"blocks": report})