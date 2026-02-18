#!/usr/bin/env python

import argparse
import json
import typing

import structlog

from clients.neptune_client import NeptuneClient, NeptuneEnvironment
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def extract_sns_messages_from_sqs_event(event: dict) -> list[str]:
    queries = []

    for record in event["Records"]:
        query = json.loads(record["body"])["Message"]
        queries.append(query)

    return queries


def handler(
    queries: list[str],
    execution_context: ExecutionContext | None = None,
    neptune_environment: NeptuneEnvironment = "prod",
) -> None:
    setup_logging(execution_context)

    neptune_client = NeptuneClient(neptune_environment)

    logger.info("Received queries", query_count=len(queries))

    for query in queries:
        neptune_client.run_open_cypher_query(query)


def lambda_handler(event: dict, context: typing.Any) -> None:
    queries = extract_sns_messages_from_sqs_event(event)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="graph_indexer",
    )
    handler(queries, execution_context)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--cypher-query",
        type=str,
        help="An openCypher query to run against the Neptune cluster.",
        required=True,
    )
    parser.add_argument(
        "--neptune-environment",
        type=str,
        help="Which Neptune cluster to connect to.",
        required=False,
        choices=["prod", "dev"],
        default="dev",
    )
    args = parser.parse_args()

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_indexer",
    )
    handler(
        [args.cypher_query],
        execution_context,
        neptune_environment=args.neptune_environment,
    )


if __name__ == "__main__":
    local_handler()
