import argparse
import typing
from datetime import datetime, timedelta

import boto3
import httpx
import smart_open
from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX, SLACK_SECRET_ID
from ingestor_loader_monitor import IngestorLoaderMonitorConfig, LoaderReport
from ingestor_trigger_monitor import IngestorTriggerMonitorConfig, TriggerReport
from models.step_events import ReporterEvent


class ReporterConfig(BaseModel):
    ingestor_s3_bucket: str = INGESTOR_S3_BUCKET
    ingestor_s3_prefix: str = INGESTOR_S3_PREFIX
    slack_secret: str | None = SLACK_SECRET_ID
    is_local: bool = False


class FinalReport(BaseModel):
    pipeline_date: str
    job_id: str
    previous_job_id: str
    neptune_record_count: int
    previous_neptune_record_count: int
    es_record_count: int | None
    previous_es_record_count: int | None


def build_final_report(
    current_report: TriggerReport | LoaderReport,
    latest_report: TriggerReport | LoaderReport,
    config: IngestorTriggerMonitorConfig | IngestorLoaderMonitorConfig,
) -> None:
    report_name = "report.final.json"
    s3_url_final_report = f"s3://{config.ingestor_s3_bucket}/{config.ingestor_s3_prefix}/{current_report.pipeline_date}/{current_report.job_id}/{report_name}"
    transport_params = {"client": boto3.client("s3")}

    final_report = None
    try:
        with smart_open.open(s3_url_final_report, "r") as f:
            final_report = FinalReport.model_validate_json(f.read())
    # if file does not exist, ignore and create one
    except (OSError, KeyError) as e:
        print(f"No final report found: {e}. Creating report.")

    if final_report is None:
        final_report = FinalReport(
            pipeline_date=current_report.pipeline_date,
            job_id=current_report.job_id,
            previous_job_id=latest_report.job_id,
            neptune_record_count=current_report.record_count,
            previous_neptune_record_count=latest_report.record_count,
            es_record_count=None,
            previous_es_record_count=None,
        )
        with smart_open.open(
            s3_url_final_report, "w", transport_params=transport_params
        ) as f:
            f.write(final_report.model_dump_json())

    else:
        updated_final_report = FinalReport(
            pipeline_date=final_report.pipeline_date,
            job_id=final_report.job_id,
            previous_job_id=final_report.previous_job_id,
            neptune_record_count=final_report.neptune_record_count,
            previous_neptune_record_count=final_report.previous_neptune_record_count,
            es_record_count=current_report.record_count,
            previous_es_record_count=latest_report.record_count,
        )
        with smart_open.open(
            s3_url_final_report, "w", transport_params=transport_params
        ) as f:
            f.write(updated_final_report.model_dump_json())


def dateTimeFromJobId(job_id: str) -> str:
    start_datetime = datetime.strptime(job_id, "%Y%m%dT%H%M")
    if start_datetime.date() == datetime.now().date():
        return start_datetime.strftime("today at %-I:%M %p %Z")
    elif start_datetime.date() == (datetime.now() - timedelta(days=1)).date():
        return start_datetime.strftime("yesterday at %-I:%M %p %Z")
    else:
        return start_datetime.strftime("on %A, %B %-d at %-I:%M %p %Z")


def get_report(event: ReporterEvent, config: ReporterConfig) -> list[typing.Any]:
    pipeline_date = event.pipeline_date or "dev"
    job_id = event.job_id
    success_count = event.success_count

    if job_id is None:
        return [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "emoji": True,
                    "text": ":rotating_light: Concepts ",
                },
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": "*Could not produce report*: job_id not supplied.",
                },
            },
        ]

    s3_url_final_report = f"s3://{config.ingestor_s3_bucket}/{config.ingestor_s3_prefix}/{pipeline_date}/{job_id}/report.final.json"

    try:
        with smart_open.open(s3_url_final_report, "r") as f:
            final_report = FinalReport.model_validate_json(f.read())
    # if file does not exist, ignore
    except (OSError, KeyError) as e:
        print(f"Report not found: {e}")

    start_datetime = dateTimeFromJobId(job_id)
    previous_start_datetime = dateTimeFromJobId(final_report.previous_job_id)
    current_run_duration = round(
        divmod(
            (datetime.now() - datetime.strptime(job_id, "%Y%m%dT%H%M")).total_seconds(),
            60,
        )[0]
    )

    if final_report.neptune_record_count == success_count:
        graph_index_comparison = "_(the same as the graph)_"
    else:
        graph_index_comparison = (
            f":warning: _compared to {final_report.neptune_record_count} in the graph_"
        )

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
                        f"- It contains *{success_count}* documents {graph_index_comparison}.",
                        f"- Pipeline took *{current_run_duration} minutes* to complete.",
                        f"- The last update was {previous_start_datetime}when {final_report.previous_es_record_count} documents were indexed.",
                    ]
                ),
            },
        },
    ]


def publish_report(report: list[typing.Any], config: ReporterConfig) -> None:
    secretsmanager = boto3.Session().client("secretsmanager")
    slack_endpoint = secretsmanager.get_secret_value(SecretId=config.slack_secret)[
        "SecretString"
    ]

    httpx.post(slack_endpoint, json={"blocks": report})


def handler(event: ReporterEvent, config: ReporterConfig) -> None:
    print("Preparing concepts pipeline report ...")

    try:
        report = get_report(event, config)
        publish_report(report, config)
    except ValueError as e:
        print(f"Report failed: {e}")
        raise e

    print("Report complete.")
    return


def lambda_handler(event: ReporterEvent, context: typing.Any) -> None:
    validated_event = ReporterEvent.model_validate(event)
    config = ReporterConfig()

    handler(validated_event, config)


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
        success_count=args.success_count,
    )
    config = ReporterConfig(is_local=True)

    handler(event, config)


if __name__ == "__main__":
    local_handler()
