import typing

import requests
from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from utils.aws import get_secret, pydantic_from_s3_json, pydantic_to_s3_json


class PipelineReport(BaseModel):
    pipeline_date: str
    index_date: str
    job_id: str

class TriggerReport(PipelineReport):
    record_count: int


class LoaderReport(PipelineReport):
    record_count: int
    total_file_size: int


class IndexRemoverReport(PipelineReport):
    deleted_count: int | None
    date: str


class IndexerReport(PipelineReport):
    previous_job_id: str | None
    neptune_record_count: int
    previous_neptune_record_count: int | None
    es_record_count: int | None
    previous_es_record_count: int | None


def build_indexer_report(
    current_report: TriggerReport | LoaderReport,
    latest_report: TriggerReport | LoaderReport,
) -> None:
    report_name = "report.indexer.json"
    s3_url_current_indexer_report = f"s3://{INGESTOR_S3_BUCKET}/{INGESTOR_S3_PREFIX}/{current_report.pipeline_date}/{current_report.index_date}/{current_report.job_id}/{report_name}"
    s3_url_latest_indexer_report = f"s3://{INGESTOR_S3_BUCKET}/{INGESTOR_S3_PREFIX}/{current_report.pipeline_date}/{current_report.index_date}/{report_name}"

    indexer_report = pydantic_from_s3_json(
        IndexerReport, s3_url_current_indexer_report, ignore_missing=True
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


def publish_report(report: list[typing.Any], slack_secret: str) -> None:
    slack_endpoint = get_secret(slack_secret)

    requests.post(slack_endpoint, json={"blocks": report})
