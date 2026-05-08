import argparse
import typing

import structlog
from elasticsearch import Elasticsearch

from models.events import BasePipelineEvent
from utils.argparse import add_pipeline_event_args
from utils.elasticsearch import (
    ElasticsearchMode,
    get_client,
    get_images_augmented_index_name,
    get_merged_index_name,
)
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def open_pit(es_client: Elasticsearch, index_name: str) -> str:
    pit = es_client.open_point_in_time(index=index_name, keep_alive="15m")
    pit_id: str = pit["id"]
    logger.info(
        "Opened point in time",
        index_name=index_name,
        pit_id=pit_id[:50] + "...",  # Truncate for readability
    )
    return pit_id


def handler(
    event: BasePipelineEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
) -> dict:
    """Create points in time (PITs) on the merged and augmented indexes."""
    setup_logging(execution_context)

    es_client = get_client("graph_extractor", event.pipeline_date, es_mode)

    merged_index = get_merged_index_name(event)
    merged_pit_id = open_pit(es_client, merged_index)

    augmented_index = get_images_augmented_index_name(event)
    augmented_pit_id = open_pit(es_client, augmented_index)

    return {
        "pit_ids": {
            "merged": merged_pit_id,
            "augmented": augmented_pit_id,
        }
    }


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="graph_pit_opener",
    )
    return handler(BasePipelineEvent.model_validate(event), execution_context)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    add_pipeline_event_args(
        parser,
        {"pipeline_date", "es_mode", "index_date_merged", "index_date_augmented"},
    )

    args = parser.parse_args()
    event = BasePipelineEvent.from_argparser(args)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_pit_opener",
    )
    result = handler(event, execution_context, es_mode=args.es_mode)
    logger.info("PITs opened", pit_ids=result["pit_ids"])


if __name__ == "__main__":
    local_handler()
