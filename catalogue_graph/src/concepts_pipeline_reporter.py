import argparse
from typing import Any
from datetime import datetime, timedelta
import boto3
import tabulate
import polars as pl
from pydantic import BaseModel
from itertools import product
from models.step_events import ReporterEvent
from utils.aws import pydantic_from_s3_json, df_from_s3_parquet
from utils.slack_report import publish_report, IndexerReport, IndexRemoverReport

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX, SLACK_SECRET_ID


class ReporterConfig(BaseModel):
    ingestor_s3_bucket: str = INGESTOR_S3_BUCKET
    ingestor_s3_prefix: str = INGESTOR_S3_PREFIX
    slack_secret: str = SLACK_SECRET_ID
    is_local: bool = False

graph_sources = [
    "loc_concepts",
    "loc_names",
    "loc_locations",
    "mesh_concepts",
    "mesh_locations",
    "wikidata_linked_loc_concepts",
    "wikidata_linked_loc_locations",
    "wikidata_linked_loc_names",
    "wikidata_linked_mesh_concepts",
    "wikidata_linked_mesh_locations",
    "catalogue_concepts",
    "catalogue_works",
]

graph_entities = ["nodes", "edges"]

def report_failure_message(report_type: str): 
    return {
        "type": "section",
        "text": {
            "type": "plain_text",
            "emoji": True,
            "text": f":rotating_light: *Could not produce Concepts {report_type} report*",
        },
    }


def date_time_from_job_id(job_id: str) -> str:
    start_datetime = datetime.strptime(job_id, "%Y%m%dT%H%M")
    if start_datetime.date() == datetime.now().date():
        return start_datetime.strftime("today at %-I:%M %p %Z")
    elif start_datetime.date() == (datetime.now() - timedelta(days=1)).date():
        return start_datetime.strftime("yesterday at %-I:%M %p %Z")
    else:
        return start_datetime.strftime("on %A, %B %-d at %-I:%M %p %Z")


def get_indexer_report(event: ReporterEvent, indexer_success_count: int, config: ReporterConfig) -> list[Any]:
    pipeline_date = event.pipeline_date or "dev"
    index_date = event.index_date
    job_id = event.job_id

    report_name = "report.indexer.json"
    s3_url_indexer_report = f"s3://{config.ingestor_s3_bucket}/{config.ingestor_s3_prefix}/{pipeline_date}/{index_date}/{job_id}/{report_name}" 

    indexer_report = pydantic_from_s3_json(
      IndexerReport, s3_url_indexer_report, ignore_missing=True
    )
      
    if job_id is not None and indexer_report is not None:
        start_datetime = date_time_from_job_id(job_id)
        previous_start_datetime = date_time_from_job_id(indexer_report.previous_job_id)
        current_run_duration = int((datetime.now() - datetime.strptime(job_id, "%Y%m%dT%H%M")).total_seconds() / 60)

        if indexer_report.neptune_record_count == indexer_success_count:
            graph_index_comparison = "_(the same as the graph)_"
        else:
            graph_index_comparison = (
                f":warning: _compared to {indexer_report.neptune_record_count} in the graph_"
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
                            f"- Index *concepts-indexed-{index_date}* in pipeline-{pipeline_date}",
                            f"- Pipeline started *{start_datetime}*",
                            f"- It contains *{indexer_success_count}* documents {graph_index_comparison}.",
                            f"- Pipeline took *{current_run_duration} minutes* to complete.",
                            f"- The last update was {previous_start_datetime}when {indexer_report.previous_es_record_count} documents were indexed.",
                        ]
                    ),
                },
            }
        ]
    return [report_failure_message("Indexer")]

def get_remover_report(event: ReporterEvent, config: ReporterConfig) -> list[Any]:
    # get deletions from the graph
    graph_remover_deletions = {}

    for source, entity in product(graph_sources, graph_entities):
        df = df_from_s3_parquet(f"s3://wellcomecollection-catalogue-graph/graph_remover/deleted_ids/{source}__{entity}.parquet")
    
        if df is not None:
            now = datetime.now()
            beginning_of_today = datetime(now.year, now.month, now.day) # should this be the last_run_date? 
            deletions_today = len(df.filter(pl.col("timestamp") > beginning_of_today).select("id").to_series().unique().to_list())
            if deletions_today > 0:
              graph_remover_deletions[f"{source}__{entity}"] = deletions_today
          
        elif df is None:
            print(f"No data found for {source}__{entity}.parquet.")

    # get deletions from the index
    pipeline_date = event.pipeline_date or "dev"
    index_date = event.index_date

    s3_url_index_remover_report = f"s3://{config.ingestor_s3_bucket}/{config.ingestor_s3_prefix}/{pipeline_date}/{index_date}/report.index_remover.json"

    index_remover_report = pydantic_from_s3_json(
        IndexRemoverReport, s3_url_index_remover_report, ignore_missing=True
    )

    # format the report for Slack
    header_slack = {
        "type": "header",
        "text": {
            "type": "plain_text",
            "text": "Concepts Remover",
        }
    }

    if bool(graph_remover_deletions):
        table = tabulate.tabulate(
              graph_remover_deletions.items(),
              headers=["Type", "Deletions"],
              colalign=("right", "left"),
        )
        graph_remover_slack = {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": "\n".join(
                    [
                        f"*Graph Remover*",
                        f"```\n{table}\n```"
                    ]
                ),
            },
        }
    else: graph_remover_slack = {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": "\n".join(
                    [
                        f"*Graph Remover*",
                        "No nodes or edges were deleted from the graph." # This potentially hides failure to retrieve the parquet file(s)
                    ]
                ),
            },
    }
    
    index_remover_slack = {
        "type": "section",
        "text": {
            "type": "mrkdwn",
            "text": "\n".join(
                [   
                    f"*Index Remover*",
                    f"Index *concepts-indexed-{index_date}* in pipeline-{pipeline_date}",
                    f"- *{index_remover_report.deleted_count}* documents were deleted",
                ]
            ),
        },
    } if index_remover_report is not None else report_failure_message("Index Remover")
        
    return [header_slack, graph_remover_slack, index_remover_slack]

def handler(events: list[ReporterEvent], config: ReporterConfig) -> None:
    print("Preparing concepts pipeline reports ...")
    total_indexer_success = sum(event.success_count for event in events)

    try:
        indexer_report = get_indexer_report(events[0], total_indexer_success, config)
        remover_report = get_remover_report(events[0], config)
        publish_report(indexer_report, config.slack_secret)
        publish_report(remover_report, config.slack_secret)

    except ValueError as e:
        print(f"Report failed: {e}")
        raise e

    print("Report complete.")
    return


def lambda_handler(events: list[ReporterEvent], context: Any) -> None:
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
        help="How many documents were successfully indexed at the previous pipeline step",
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