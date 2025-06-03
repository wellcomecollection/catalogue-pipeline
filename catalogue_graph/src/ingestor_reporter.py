import argparse
import typing
from datetime import datetime, timedelta

import boto3
from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX, SLACK_SECRET_ID
from ingestor_loader_monitor import IngestorLoaderMonitorConfig, LoaderReport
from ingestor_trigger_monitor import IngestorTriggerMonitorConfig, TriggerReport
from models.step_events import ReporterEvent
from utils.aws import pydantic_from_s3_json, pydantic_to_s3_json
from utils.slack_report import publish_report


class ReporterConfig(BaseModel):
    ingestor_s3_bucket: str = INGESTOR_S3_BUCKET
    ingestor_s3_prefix: str = INGESTOR_S3_PREFIX
    slack_secret: str
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

    final_report = pydantic_from_s3_json(
      FinalReport, s3_url_final_report, ignore_missing=True
    )

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
        pydantic_to_s3_json(final_report, s3_url_final_report)

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
        pydantic_to_s3_json(updated_final_report, s3_url_final_report)



def date_time_from_job_id(job_id: str) -> str:
    start_datetime = datetime.strptime(job_id, "%Y%m%dT%H%M")
    if start_datetime.date() == datetime.now().date():
        return start_datetime.strftime("today at %-I:%M %p %Z")
    elif start_datetime.date() == (datetime.now() - timedelta(days=1)).date():
        return start_datetime.strftime("yesterday at %-I:%M %p %Z")
    else:
        return start_datetime.strftime("on %A, %B %-d at %-I:%M %p %Z")


def get_report(event: ReporterEvent, indexer_success_count: int, config: ReporterConfig) -> list[typing.Any]:
    pipeline_date = event.pipeline_date or "dev"
    index_date = event.index_date
    job_id = event.job_id

    s3_url_final_report = f"s3://{config.ingestor_s3_bucket}/{config.ingestor_s3_prefix}/{pipeline_date}/{index_date}/{job_id}/report.final.json"

    final_report = pydantic_from_s3_json(
      FinalReport, s3_url_final_report, ignore_missing=True
    )
      
    if job_id is not None and final_report is not None:
      start_datetime = date_time_from_job_id(job_id)
      previous_start_datetime = date_time_from_job_id(final_report.previous_job_id)
      current_run_duration = int((datetime.now() - datetime.strptime(job_id, "%Y%m%dT%H%M")).total_seconds() / 60)

      if final_report.neptune_record_count == indexer_success_count:
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
                  "text": f":white_check_mark: Concepts :bulb:",
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
                          f"- It contains *{indexer_success_count}* documents {graph_index_comparison}.",
                          f"- Pipeline took *{current_run_duration} minutes* to complete.",
                          f"- The last update was {previous_start_datetime}when {final_report.previous_es_record_count} documents were indexed.",
                      ]
                  ),
              },
          }
      ]
    return [
        {
            "type": "header",
            "text": {
                "type": "plain_text",
                "emoji": True,
                "text": ":rotating_light: Concepts :bulb:",
            },
        },
        {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": f"*Could not produce report*.",
            },
        },
    ]





def handler(events: list[ReporterEvent], config: ReporterConfig) -> None:
    print("Preparing concepts pipeline report ...")
    total_indexer_success = sum(event.success_count for event in events)

    try:
        report = get_report(events[0], total_indexer_success, config)
        publish_report(report, config.slack_secret)
    except ValueError as e:
        print(f"Report failed: {e}")
        raise e

    print("Report complete.")
    return


def lambda_handler(events: list[ReporterEvent], context: typing.Any) -> None:
    validated_events = [ReporterEvent.model_validate(event) for event in events]
    config = ReporterConfig()

    handler(validated_events, config)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="Which pipeline to report on",
        required=True,
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help="Which index to report on",
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
        index_date=args.index_date,
        job_id=args.job_id,
        success_count=args.success_count,
    )
    config = ReporterConfig(is_local=True)

    handler([event], config)


if __name__ == "__main__":
    local_handler()