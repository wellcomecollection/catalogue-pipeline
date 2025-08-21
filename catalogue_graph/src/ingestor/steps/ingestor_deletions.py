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


def get_current_id_count(pipeline_date: str, index_date: str, is_local: bool) -> int:
    """Return the number of documents currently stored in ES in the concepts index."""
    es = utils.elasticsearch.get_client("concept_ingestor", pipeline_date, is_local)

    response = es.count(index=get_standard_index_name("concepts-indexed", index_date))
    count: int = response.get("count", 0)
    return count


def delete_concepts_from_elasticsearch(
    deleted_ids: set[str],
    pipeline_date: str,
    index_date: str,
    is_local: bool,
) -> int:
    """Remove documents matching `deleted_ids` from the concepts ES index."""
    es = utils.elasticsearch.get_client("concept_ingestor", pipeline_date, is_local)
    index_name = get_standard_index_name("concepts-indexed", index_date)

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


def get_ids_to_delete(pipeline_date: str, index_date: str) -> set[str]:
    """Return a list of concept IDs marked for deletion from the ES index."""
    s3_file_uri = graph_remover.get_s3_uri("catalogue_concepts", "nodes", "deleted_ids")

    # Retrieve a log of concept IDs which were deleted from the graph (see `graph_remover.py`).
    df = df_from_s3_parquet(s3_file_uri)

    try:
        # Filter for IDs which were added to the log file since the last run of the ingestor_deletion.
        cutoff_date = get_last_run_date(pipeline_date, index_date)
        df = df.filter(pl.col("timestamp") >= cutoff_date)
    except FileNotFoundError:
        # The file might not exist on the first run, implying we did not run the ingestor deletions on the current index yet.
        # In this case, we can use the index date as a filter (since all IDs which were removed from the graph before
        # the index was created cannot exist in the index).
        if index_date and _is_valid_date(index_date):
            df = df.filter(
                pl.col("timestamp") >= datetime.strptime(index_date, "%Y-%m-%d").date()
            )

    ids = pl.Series(df.select(pl.col("id"))).to_list()
    return set(ids)


def get_last_run_date(pipeline_date: str, index_date: str) -> date:
    """Return a date corresponding to the last time we ran the ingestor_deletion Lambda."""
    ingestor_deletion_report: DeletionReport | None = DeletionReport.read(
        pipeline_date=pipeline_date, index_date=index_date
    )
    assert isinstance(ingestor_deletion_report, DeletionReport)

    return datetime.strptime(ingestor_deletion_report.date, "%Y-%m-%d").date()


def handler(
    pipeline_date: str,
    index_date: str,
    job_id: str,
    disable_safety_check: bool,
    is_local: bool = False,
) -> None:
    ids_to_delete = get_ids_to_delete(pipeline_date, index_date)
    current_id_count = get_current_id_count(pipeline_date, index_date, is_local)

    # This is part of a safety mechanism. If two sets of IDs differ by more than the DEFAULT_THRESHOLD
    # (set to 5%), an exception will be raised.
    validate_fractional_change(
        modified_size=len(ids_to_delete),
        total_size=current_id_count,
        force_pass=disable_safety_check,
    )
    deleted_count = 0
    if len(ids_to_delete) > 0:
        # Delete the corresponding items from the graph
        deleted_count = delete_concepts_from_elasticsearch(
            ids_to_delete, pipeline_date, index_date, is_local
        )

    report = DeletionReport(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=job_id,
        deleted_count=deleted_count,
        date=datetime.today().strftime("%Y-%m-%d"),
    )

    report.write()
    report.write(latest=True)


def lambda_handler(event: dict, context: typing.Any) -> dict:
    validated_event = IngestorMonitorStepEvent(**event)

    handler(
        validated_event.pipeline_date,
        validated_event.index_date,
        validated_event.job_id,
        validated_event.force_pass,
    )

    return IngestorStepEvent(**event).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--disable-safety-check",
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
        help='The concepts index date that is being ingested to, will default to "dev".',
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

    handler(**args.__dict__, is_local=True)


if __name__ == "__main__":
    local_handler()
