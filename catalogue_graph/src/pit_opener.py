import argparse
import typing

import structlog

from models.events import BasePipelineEvent
from utils.argparse import add_pipeline_event_args
from utils.elasticsearch import ElasticsearchMode, get_client, get_merged_index_name
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def handler(
    event: BasePipelineEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
) -> dict:
    """Create a point in time (PIT) on the merged index and return its PIT ID."""
    setup_logging(execution_context)

    es_client = get_client("graph_extractor", event.pipeline_date, es_mode)

    index_name = get_merged_index_name(event)

    pit = es_client.open_point_in_time(index=index_name, keep_alive="15m")

    logger.info(
        "Opened point in time",
        index_name=index_name,
        pit_id=pit["id"][:50] + "...",  # Truncate for readability
    )

    return {"pit_id": pit["id"]}


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="graph_pit_opener",
    )
    return handler(BasePipelineEvent(**event), execution_context)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    add_pipeline_event_args(parser, {"pipeline_date", "es_mode"})

    args = parser.parse_args()
    event = BasePipelineEvent(**args.__dict__)

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_pit_opener",
    )
    result = handler(event, execution_context, es_mode=args.es_mode)
    logger.info("PIT opened", pit_id=result["pit_id"])


if __name__ == "__main__":
    local_handler()
