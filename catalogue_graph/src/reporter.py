import typing
import argparse

import boto3
import httpx
import smart_open
from datetime import datetime, timedelta
from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX, SLACK_SECRET_ID
from ingestor_trigger_monitor import TriggerReport
from ingestor_loader_monitor import LoaderReport
from models.step_events import ReporterEvent

class ReporterConfig(BaseModel):
    s3_bucket: str = INGESTOR_S3_BUCKET
    ingestor_s3_prefix: str = INGESTOR_S3_PREFIX
    slack_secret: str | None = SLACK_SECRET_ID
    is_local: bool = False

class FinalReport(BaseModel):
    pipeline_date: str
    job_id: str | None = None
    neptune_record_count: int
    es_record_count: int
    success_count: int
    previous_es_record_count: int
    previous_job_id: str


def dateTimeFromJobId(job_id: str) -> str:
    start_datetime = datetime.strptime(job_id, "%Y%m%dT%H%M")
    if start_datetime.date() == datetime.now().date():
        return start_datetime.strftime("today at %-I:%M %p %Z")
    elif start_datetime.date() == (datetime.now() - timedelta(days=1)).date():
        return start_datetime.strftime("yesterday at %-I:%M %p %Z")
    else:
        return start_datetime.strftime("on %A, %B %-d at %-I:%M %p %Z")
    
def get_report(
    event: ReporterEvent, config: ReporterConfig
) -> list[typing.Any]:
    pipeline_date = event.pipeline_date or "dev"
    job_id = event.job_id

    if job_id is None:
        return [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "emoji": True,
                    "text": f":rotating_light: Concepts ",
                },
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"*Could not produce report*: job_id not supplied.",
                },
            },
        ]

    trigger_report_url = f"s3://{config.s3_bucket}/{config.ingestor_s3_prefix}/{pipeline_date}/{job_id}/report.trigger.json"
    loader_report_url = f"s3://{config.s3_bucket}/{config.ingestor_s3_prefix}/{pipeline_date}/{job_id}/report.loader.json"
    previous_loader_report_url = f"s3://{config.s3_bucket}/{config.ingestor_s3_prefix}/{pipeline_date}/report.loader.json"

    # open with smart_open, check for file existence
    try:
        with smart_open.open(trigger_report_url, "r") as f:
            trigger_report = TriggerReport.model_validate_json(f.read())
        with smart_open.open(loader_report_url, "r") as f:
            loader_report = LoaderReport.model_validate_json(f.read())
        with smart_open.open(previous_loader_report_url, "r") as f:
            previous_loader_report = LoaderReport.model_validate_json(f.read())
    # if file does not exist, ignore
    except (OSError, KeyError) as e:
        print(f"Report not found: {e}")

    final_report = FinalReport(
      pipeline_date=pipeline_date,
      job_id=job_id,
      neptune_record_count=trigger_report.record_count,
      es_record_count=loader_report.record_count,
      success_count=event.success_count,
      previous_es_record_count=previous_loader_report.record_count,
      previous_job_id=previous_loader_report.job_id
    )

    start_datetime = dateTimeFromJobId(job_id)
    previous_start_datetime = dateTimeFromJobId(final_report.previous_job_id)
    time_delta = round(divmod((datetime.now() - datetime.strptime(job_id, "%Y%m%dT%H%M")).total_seconds(), 60)[0])

    if final_report.neptune_record_count == final_report.success_count:
      graph_index_comparison = "_(the same as the graph)_"
    else:
      graph_index_comparison = f":warning: _compared to {final_report.neptune_record_count} in the graph_"

    return [
        {
            "type": "header",
            "text": {
                "type": "plain_text",
                "emoji": True,
                "text": f"{':white_check_mark:'} Concepts :bulb:",
            },
        },
        {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": "\n".join(
                    [
                        f"- Index *concepts-indexed-{pipeline_date}*",
                        f"- Pipeline started *{start_datetime}*",
                        f"- It contains *{final_report.success_count}* documents {graph_index_comparison}.",
                        f"- Pipeline took *{time_delta} minutes* to complete.",
                        f"- The last update was {previous_start_datetime}when {final_report.previous_es_record_count} documents were indexed."
                    ]
                ),
            },
        },
    ]


def publish_report(
    report: list[typing.Any],
    config: ReporterConfig
) -> None:
    secretsmanager = boto3.Session().client("secretsmanager")
    slack_endpoint = secretsmanager.get_secret_value(SecretId=config.slack_secret)["SecretString"]

    httpx.post(slack_endpoint, json={ "blocks": report })

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

def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="Which pipeline to report on",
        required=True,
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="The job to report on",
        required=False,
    )
    parser.add_argument(
        "--success-count",
        type=str,
        help="The job to report on",
        required=False,
    )
    args = parser.parse_args()

    event = ReporterEvent(
        pipeline_date=args.pipeline_date,
        job_id=args.job_id,
        success_count=args.success_count
    )
    config = ReporterConfig(is_local=True)

    handler(event, config)

if __name__ == "__main__":
    local_handler()