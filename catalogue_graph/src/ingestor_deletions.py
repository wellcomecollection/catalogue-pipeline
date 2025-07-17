import argparse
import typing
from datetime import date, datetime

import config
import polars as pl
import utils.elasticsearch
from graph_remover import DELETED_IDS_FOLDER
from models.step_events import IngestorMonitorStepEvent, IngestorStepEvent
from utils.aws import df_from_s3_parquet, pydantic_from_s3_json
from utils.reporting import DeletionReport
from utils.safety import validate_fractional_change


def _get_concepts_index_name(index_date: str | None) -> str:
    return (
        "concepts-indexed" if index_date is None else f"concepts-indexed-{index_date}"
    )


def get_current_id_count(
    pipeline_date: str | None, index_date: str | None, is_local: bool
) -> int:
    """Return the number of documents currently stored in ES in the concepts index."""
    es = utils.elasticsearch.get_client("concept_ingestor", pipeline_date, is_local)

    response = es.count(index=_get_concepts_index_name(index_date))
    count: int = response.get("count", 0)
    return count


def delete_concepts_from_elasticsearch(
    deleted_ids: set[str],
    pipeline_date: str | None,
    index_date: str | None,
    is_local: bool,
) -> int:
    """Remove documents matching `deleted_ids` from the concepts ES index."""
    es = utils.elasticsearch.get_client("concept_ingestor", pipeline_date, is_local)
    index_name = _get_concepts_index_name(index_date)

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


def get_ids_to_delete(pipeline_date: str | None, index_date: str | None) -> set[str]:
    """Return a list of concept IDs marked for deletion from the ES index."""
    s3_file_uri = f"s3://{config.INGESTOR_S3_BUCKET}/{DELETED_IDS_FOLDER}/catalogue_concepts__nodes.parquet"

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


def get_last_run_date(pipeline_date: str | None, index_date: str | None) -> date:
    """Return a date corresponding to the last time we ran the ingestor_deletion Lambda."""
    pipeline_date = pipeline_date or "dev"
    index_date = index_date or "dev"

    ingestor_deletion_report: DeletionReport | None = DeletionReport.read(
        pipeline_date=pipeline_date,
        index_date=index_date,
        ignore_missing=True,
    )

    # We shouldn't get here as DeletionReport.read should throw its own error,
    # throw a RuntimeError here, to make it clear that the state of the ingestor deletions is unexpected.
    if ingestor_deletion_report is None:
        # TO DO: remove this when we have a report.deletions.json report
        s3_url = f"s3://{config.INGESTOR_S3_BUCKET}/{config.INGESTOR_S3_PREFIX}/{pipeline_date}/{index_date}/report.index_remover.json"
        index_remover_report = pydantic_from_s3_json(DeletionReport, s3_url)
        if index_remover_report is None:
            raise RuntimeError(
                f"Unexpected error: DeletionReport for pipeline_date={pipeline_date} and index_date={index_date} not found."
            )
        return datetime.strptime(index_remover_report.date, "%Y-%m-%d").date()

    return datetime.strptime(ingestor_deletion_report.date, "%Y-%m-%d").date()


def handler(
    pipeline_date: str | None,
    index_date: str | None,
    job_id: str | None,
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
        pipeline_date=pipeline_date or "dev",
        index_date=index_date or "dev",
        job_id=job_id or "dev",
        deleted_count=deleted_count,
        date=datetime.today().strftime("%Y-%m-%d"),
    )

    report.write()
    report.write(latest=True)


def lambda_handler(event: IngestorMonitorStepEvent, context: typing.Any) -> dict:
    validated_event = IngestorMonitorStepEvent.model_validate(event)

    handler(
        validated_event.pipeline_date,
        validated_event.index_date,
        validated_event.job_id,
        bool(validated_event.force_pass),
    )

    return IngestorStepEvent(
        pipeline_date=validated_event.pipeline_date,
        index_date=validated_event.index_date,
        job_id=validated_event.job_id,
    ).model_dump()


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
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help='The concepts index date that is being ingested to, will default to "dev".',
        required=False
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
