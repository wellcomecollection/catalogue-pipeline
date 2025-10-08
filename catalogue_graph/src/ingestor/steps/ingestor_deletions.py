import argparse
import typing
from datetime import datetime

import polars as pl

import graph_remover
import utils.elasticsearch
from ingestor.models.step_events import IngestorMonitorStepEvent, IngestorStepEvent
from utils.aws import df_from_s3_parquet
from utils.elasticsearch import ElasticsearchMode, get_standard_index_name
from utils.reporting import DeletionReport
from utils.safety import validate_fractional_change


def get_current_id_count(
    event: IngestorMonitorStepEvent, es_mode: ElasticsearchMode
) -> int:
    """Return the number of documents currently stored in ES in the concepts index."""
    es = utils.elasticsearch.get_client(
        "concepts_ingestor", event.pipeline_date, es_mode
    )

    response = es.count(
        index=get_standard_index_name("concepts-indexed", event.index_date)
    )
    count: int = response.get("count", 0)
    return count


def delete_concepts_from_elasticsearch(
    deleted_ids: set[str],
    event: IngestorMonitorStepEvent,
    es_mode: ElasticsearchMode,
) -> int:
    """Remove documents matching `deleted_ids` from the concepts ES index."""
    es = utils.elasticsearch.get_client(
        "concepts_ingestor", event.pipeline_date, es_mode
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

    # TODO: Fix this based on https://github.com/wellcomecollection/platform/issues/6121
    if event.index_date and _is_valid_date(event.index_date):
        index_date = datetime.strptime(event.index_date, "%Y-%m-%d").date()
        df = df.filter(pl.col("timestamp") >= index_date)

    ids = pl.Series(df.select(pl.col("id"))).to_list()
    return set(ids)


def handler(
    event: IngestorMonitorStepEvent, es_mode: ElasticsearchMode = "private"
) -> None:
    ids_to_delete = get_ids_to_delete(event)
    current_id_count = get_current_id_count(event, es_mode)

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
            ids_to_delete, event, es_mode
        )

    report = DeletionReport(
        **event.model_dump(),
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
    handler(event, es_mode="public")


if __name__ == "__main__":
    local_handler()
