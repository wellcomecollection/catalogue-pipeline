import argparse
import typing
from datetime import date, datetime

import config
import polars as pl
import smart_open
import utils.elasticsearch
from graph_remover import DELETED_IDS_FOLDER
from utils.aws import df_from_s3_parquet

# This is part of a safety mechanism. If two sets of IDs differ by more than 5%, an exception will be raised.
ACCEPTABLE_DIFF_THRESHOLD = 0.05


def _get_last_index_remover_run_file_uri(pipeline_date: str | None) -> str:
    return f"s3://{config.INGESTOR_S3_BUCKET}/ingestor/{pipeline_date or 'dev'}/last_index_remover_run_date.txt"


def _get_concepts_index_name(pipeline_date: str | None) -> str:
    return (
        "concepts-indexed"
        if pipeline_date is None
        else f"concepts-indexed-{pipeline_date}"
    )


def get_current_id_count(pipeline_date: str | None, is_local: bool) -> int:
    """Return the number of documents currently stored in ES in the concepts index."""
    es = utils.elasticsearch.get_client(pipeline_date, is_local)

    response = es.count(index=_get_concepts_index_name(pipeline_date))
    count: int = response.get("count", 0)
    return count


def delete_concepts_from_elasticsearch(
    deleted_ids: set[str], pipeline_date: str | None, is_local: bool
) -> None:
    """Remove documents matching `deleted_ids` from the concepts ES index."""
    es = utils.elasticsearch.get_client(pipeline_date, is_local)
    index_name = _get_concepts_index_name(pipeline_date)

    response = es.delete_by_query(
        index=index_name, body={"query": {"ids": {"values": list(deleted_ids)}}}
    )

    deleted_count = response["deleted"]
    print(f"Deleted {deleted_count} documents from the {index_name} index.")


def get_ids_to_delete(pipeline_date: str | None) -> set[str]:
    """Return a list of concept IDs marked for deletion from the ES index."""
    s3_file_uri = f"s3://{config.INGESTOR_S3_BUCKET}/{DELETED_IDS_FOLDER}/catalogue_concepts__nodes.parquet"

    # Retrieve a log of concept IDs which were deleted from the graph (see `graph_remover.py`).
    df = df_from_s3_parquet(s3_file_uri)

    try:
        # Filter for IDs which were added to the log file since the last run of the index_remover.
        cutoff_date = get_last_run_date(pipeline_date)
        df = df.filter(pl.col("timestamp") >= cutoff_date)
    except (OSError, KeyError):
        # The file might not exist on the first run, which implies that no filter should be applied.
        pass

    ids = pl.Series(df.select(pl.col("id"))).to_list()
    return set(ids)


def get_last_run_date(pipeline_date: str | None) -> date:
    """Return a date corresponding to the last time we ran the index_remover Lambda."""
    s3_uri = _get_last_index_remover_run_file_uri(pipeline_date)
    with smart_open.open(s3_uri, "r") as f:
        formatted_date = f.read()

    return datetime.strptime(formatted_date, "%Y-%m-%d").date()


def update_last_run_date(pipeline_date: str | None) -> None:
    """Update the S3 file storing the date of the last index_remover run with today's date."""
    s3_uri = _get_last_index_remover_run_file_uri(pipeline_date)
    formatted_today = datetime.today().strftime("%Y-%m-%d")
    with smart_open.open(s3_uri, "w") as f:
        f.write(formatted_today)


def handler(
    pipeline_date: str | None,
    disable_safety_check: bool,
    is_local: bool = False,
) -> None:
    ids_to_delete = get_ids_to_delete(pipeline_date)
    current_id_count = get_current_id_count(pipeline_date, is_local)
    
    if (
        current_id_count > 0
        and len(ids_to_delete) / current_id_count > ACCEPTABLE_DIFF_THRESHOLD
        and not disable_safety_check
    ):
        raise ValueError(
            f"Attempted to remove {len(ids_to_delete)} documents (out of a total of {current_id_count}), which is above the safety threshold."
        )

    if len(ids_to_delete) > 0:
        # Delete the corresponding items from the graph
        delete_concepts_from_elasticsearch(ids_to_delete, pipeline_date, is_local)

    update_last_run_date(pipeline_date)


def lambda_handler(event: dict, context: typing.Any) -> None:
    pipeline_date = event["pipeline_date"]
    override_safety_check = event.get("override_safety_check", False)

    handler(pipeline_date, override_safety_check)


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

    args = parser.parse_args()

    handler(**args.__dict__, is_local=True)


if __name__ == "__main__":
    local_handler()
