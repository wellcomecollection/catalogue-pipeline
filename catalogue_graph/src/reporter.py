import typing

import boto3
import httpx
import smart_open
from datetime import datetime
from pydantic import BaseModel

from clients.metric_reporter import MetricReporter
from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX, SLACK_SECRET_ID
from ingestor_loader import IngestorLoaderLambdaEvent

class ReporterEvent(BaseModel):
    pipeline_date: str | None = None

class ReporterConfig(BaseModel):
    s3_bucket: str = INGESTOR_S3_BUCKET
    ingestor_s3_prefix: str = INGESTOR_S3_PREFIX
    slack_secret: str = SLACK_SECRET_ID
    is_local: bool = False

class FinalReport(BaseModel):
    previous_job_id: str
    previous_neptune_record_count: int
    previous_es_record_count: int
    current_job_id: str
    current_neptune_record_count: int
    current_es_record_count: int
    pipeline_date: str


def get_report(
    event: ReporterEvent, config: ReporterConfig
) -> FinalReport:
    pipeline_date = event.pipeline_date or "dev"

    s3_report_name = "report.final.json"
    s3_report_url = f"s3://{config.s3_bucket}/{config.ingestor_s3_prefix}/{pipeline_date}/{s3_report_name}"

    final_report = None
    # open with smart_open, check for file existence
    try:
        with smart_open.open(s3_report_url, "r") as f:
            final_report = FinalReport.model_validate_json(f.read())
    # if file does not exist, ignore
    except (OSError, KeyError) as e:
        print(f"No latest pipeline report found: {e}")

    # final_report = FinalReport(
    #   previous_job_id="jobby_1",
    #   previous_neptune_record_count=42,
    #   previous_es_record_count=42,
    #   current_job_id="jobby_2",
    #   current_neptune_record_count=42,
    #   current_es_record_count=42,
    #   pipeline_date="live_pipeline"
    # )

    start_datetime = datetime.strptime(final_report.current_job_id, "%Y%m%dT%H%M")
    formatted_start_datetime = start_datetime.strftime("%A %B %d, %Y at %H:%M")
    time_delta = round(divmod((datetime.now() - start_datetime).total_seconds(), 60)[0])

    return [
        {
            "type": "header",
            "text": {
                "type": "plain_text",
                "emoji": True,
                "text": ":bulb: Catalogue concepts pipeline report",
            },
        },
        {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": "\n".join(
                    [
                        f"Pipeline started at *{formatted_start_datetime}* and ran successfully in *{time_delta}* minutes.",
                        f"Previous run yielded *{final_report.previous_es_record_count} Elasticsearch documents* from *{final_report.previous_neptune_record_count} Neptune records*.",
                        f"Current run yielded *{final_report.current_es_record_count} Elasticsearch documents* from *{final_report.current_neptune_record_count} Neptune records*."
                    ]
                ),
            },
        },
    ]


def publish_report(
    report: str,
    config: ReporterConfig
) -> None:
    secretsmanager = boto3.Session().client("secretsmanager")
    slack_endpoint = secretsmanager.get_secret_value(SecretId=config.slack_secret)["SecretString"]
    
    httpx.post(slack_endpoint, json=report)

def handler(
    event: ReporterEvent, context: typing.Any
) -> None:
    print("Preparing concepts pipeline report ...")
    config = ReporterConfig()
    
    try:
        report = get_report(event, config)
        publish_report(report, config)
    except ValueError as e:
        print(f"Report failed: {e}")
        raise e

    print("Report complete.")
    return
