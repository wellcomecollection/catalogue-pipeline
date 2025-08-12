import argparse
from datetime import datetime, timedelta
from typing import Any

from pydantic import BaseModel

from config import SLACK_SECRET_ID
from ingestor.models.step_events import IngestorStepEvent
from utils.reporting import DeletionReport, IndexerReport, TriggerReport
from utils.slack import publish_report


class ReporterConfig(BaseModel):
    slack_secret: str = SLACK_SECRET_ID
    is_local: bool = False


def report_failure_message(report_type: str) -> dict[Any, Any]:
    return {
        "type": "section",
        "text": {
            "type": "plain_text",
            "emoji": True,
            "text": f":rotating_light: *Could not produce Concepts {report_type} report*",
        },
    }


slack_header = [
    {
        "type": "header",
        "text": {
            "type": "plain_text",
            "emoji": True,
            "text": ":white_check_mark: Concepts :bulb:",
        },
    }
]


def date_time_from_job_id(job_id: str = "") -> str:
    start_datetime = datetime.strptime(job_id, "%Y%m%dT%H%M")
    if start_datetime.date() == datetime.now().date():
        return start_datetime.strftime("today at %-I:%M %p %Z")
    elif start_datetime.date() == (datetime.now() - timedelta(days=1)).date():
        return start_datetime.strftime("yesterday at %-I:%M %p %Z")
    else:
        return start_datetime.strftime("on %A, %B %-d at %-I:%M %p %Z")


def get_ingestor_report(event: IngestorStepEvent, config: ReporterConfig) -> list[Any]:
    pipeline_date = event.pipeline_date or "dev"
    index_date = event.index_date or "dev"
    job_id = event.job_id

    trigger_report: TriggerReport | None = TriggerReport.read(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
        ignore_missing=True,
    )

    indexer_report: IndexerReport | None = IndexerReport.read(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
        ignore_missing=True,
    )

    previous_indexer_report: IndexerReport | None = (
        IndexerReport.read(
            pipeline_date=pipeline_date,
            index_date=index_date,
            job_id=indexer_report.previous_job_id,
            ignore_missing=True,
        )
        if indexer_report and indexer_report.previous_job_id
        else None
    )

    deletions_report: DeletionReport | None = DeletionReport.read(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
        ignore_missing=True,
    )

    if job_id is not None and trigger_report is not None and indexer_report is not None:
        start_datetime = date_time_from_job_id(job_id)

        if trigger_report.record_count == indexer_report.success_count:
            graph_index_comparison = "_(the same as the graph)_"
        else:
            graph_index_comparison = (
                f":warning: _compared to {trigger_report.record_count} in the graph_"
            )

        current_run_duration = int(
            (datetime.now() - datetime.strptime(job_id, "%Y%m%dT%H%M")).total_seconds()
            / 60
        )

        if deletions_report:
            ingestor_deletions_line = f"- *{deletions_report.deleted_count}* documents were deleted from the index."
        else:
            ingestor_deletions_line = "- No deletions report found."

        if previous_indexer_report and previous_indexer_report.job_id:
            last_update_line = (
                f"- The last update was {date_time_from_job_id(previous_indexer_report.job_id)}"
                f"when {previous_indexer_report.success_count} documents were indexed."
            )
        else:
            last_update_line = "- No previous indexer report found."

        return [
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": "\n".join(
                        [
                            f"- Index *concepts-indexed-{index_date}* in pipeline-{pipeline_date}",
                            f"- Pipeline started *{start_datetime}*",
                            f"- It contains *{indexer_report.success_count}* documents {graph_index_comparison}.",
                            f"- Pipeline took *{current_run_duration} minutes* to complete.",
                            ingestor_deletions_line,
                            last_update_line,
                        ]
                    ),
                },
            }
        ]
    return [report_failure_message("Indexer")]


def handler(event: IngestorStepEvent, config: ReporterConfig) -> None:
    print("Preparing concepts pipeline reports ...")

    ingestor_report = get_ingestor_report(event, config)
    publish_report(slack_header + ingestor_report, config.slack_secret)

    print("Report complete.")


def lambda_handler(event: IngestorStepEvent, context: Any) -> None:
    validated_event = IngestorStepEvent.model_validate(event)
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

    args = parser.parse_args()

    event = IngestorStepEvent(**args.__dict__)
    config = ReporterConfig(is_local=True)

    handler(event, config)


if __name__ == "__main__":
    local_handler()
