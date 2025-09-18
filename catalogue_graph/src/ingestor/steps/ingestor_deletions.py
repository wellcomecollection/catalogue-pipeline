import argparse
import typing
from datetime import date, datetime

import polars as pl

import graph_remover
import utils.elasticsearch
from ingestor.models.step_events import IngestorMonitorStepEvent, IngestorStepEvent
from utils.aws import df_from_s3_parquet
from utils.elasticsearch import get_standard_index_name
from utils.reporting import DeletionReport
from utils.safety import validate_fractional_change


def get_current_id_count(event: IngestorMonitorStepEvent, is_local: bool) -> int:
    """Return the number of documents currently stored in ES in the concepts index."""
    es = utils.elasticsearch.get_client(
        "concept_ingestor", event.pipeline_date, is_local
    )

    response = es.count(
        index=get_standard_index_name("concepts-indexed", event.index_date)
    )
    count: int = response.get("count", 0)
    return count


def delete_concepts_from_elasticsearch(
    deleted_ids: set[str],
    event: IngestorMonitorStepEvent,
    is_local: bool,
) -> int:
    """Remove documents matching `deleted_ids` from the concepts ES index."""
    es = utils.elasticsearch.get_client(
        "concept_ingestor", event.pipeline_date, is_local
    )
    index_name = get_standard_index_name("concepts-indexed", event.index_date)

    response = es.delete_by_query(
        index=index_name, body={"query": {"ids": {"values": list(deleted_ids)}}}
    )

    deleted_count: int = response["deleted"]
    print(f"Deleted {deleted_count} documents from the {index_name} index.")
    return deleted_count


def _is_valid_date(index_date: str) -> bool:
    try:
        datetime.strptime(index_date, "%Y-%m-%d")
        return True
    except ValueError:
        return False


def get_ids_to_delete(event: IngestorMonitorStepEvent) -> set[str]:
    """Return a list of concept IDs marked for deletion from the ES index."""
    s3_file_uri = graph_remover.get_s3_uri("catalogue_concepts", "nodes", "deleted_ids")

    # Retrieve a log of concept IDs which were deleted from the graph (see `graph_remover.py`).
    df = df_from_s3_parquet(s3_file_uri)

    try:
        # Filter for IDs which were added to the log file since the last run of the ingestor_deletion.
        cutoff_date = get_last_run_date(event)
        df = df.filter(pl.col("timestamp") >= cutoff_date)
    except FileNotFoundError:
        # If the file does not exist, it means we did not run ingestor deletions on the current index yet.
        # In this case, we can use the index date as a filter (since all IDs which were removed from the graph before
        # the index was created cannot exist in the index).
        if event.index_date and _is_valid_date(event.index_date):
            index_date = datetime.strptime(event.index_date, "%Y-%m-%d").date()
            df = df.filter(pl.col("timestamp") >= index_date)

    ids = pl.Series(df.select(pl.col("id"))).to_list()
    return set(ids)


def get_last_run_date(event: IngestorMonitorStepEvent) -> date:
    """Return a date corresponding to the last time we ran the ingestor_deletion Lambda."""
    ingestor_deletion_report: DeletionReport | None = DeletionReport.read(
        pipeline_date=event.pipeline_date,
        index_date=event.index_date,
        ingestor_type=event.ingestor_type,
    )
    assert isinstance(ingestor_deletion_report, DeletionReport)

    return datetime.strptime(ingestor_deletion_report.date, "%Y-%m-%d").date()


def handler(event: IngestorMonitorStepEvent, is_local: bool = False) -> None:
    ids_to_delete = get_ids_to_delete(event)
    current_id_count = get_current_id_count(event, is_local)

    # This is part of a safety mechanism. If two sets of IDs differ by more than the DEFAULT_THRESHOLD
    # (set to 5%), an exception will be raised.
    validate_fractional_change(
        modified_size=len(ids_to_delete),
        total_size=current_id_count,
        force_pass=event.force_pass,
    )
    deleted_count = 0
    if len(ids_to_delete) > 0:
        # Delete the corresponding items from the graph
        deleted_count = delete_concepts_from_elasticsearch(
            ids_to_delete, event, is_local
        )

    report = DeletionReport(
        pipeline_date=event.pipeline_date,
        index_date=event.index_date,
        ingestor_type=event.ingestor_type,
        job_id=event.job_id,
        deleted_count=deleted_count,
        date=datetime.today().strftime("%Y-%m-%d"),
    )
    report.write()


def lambda_handler(event: dict, context: typing.Any) -> dict:
    handler(IngestorMonitorStepEvent(**event))
    return IngestorStepEvent(**event).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--force-pass",
        type=bool,
        help="Whether to override a safety check which prevents document removal if the percentage of removed items is above a certain threshold.",
        default=False,
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="The pipeline date corresponding to the concepts index to remove from.",
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help='The concepts index date that is being removed from, will default to "dev".',
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="The job ID for the current ingestor run.",
        required=False,
        default="dev",
    )

    args = parser.parse_args()
    event = IngestorMonitorStepEvent(**args.__dict__, ingestor_type="concepts")
    handler(event, is_local=True)


if __name__ == "__main__":
    local_handler()
