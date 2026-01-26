import argparse
import typing

import boto3
import structlog

import config
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def scale_cluster(min_capacity: float, max_capacity: float) -> None:
    client = boto3.client("neptune")
    client.modify_db_cluster(
        DBClusterIdentifier=config.NEPTUNE_CLUSTER_IDENTIFIER,
        ServerlessV2ScalingConfiguration={
            "MinCapacity": min_capacity,
            "MaxCapacity": max_capacity,
        },
        ApplyImmediately=True,
    )


def handler(
    min_capacity: float,
    max_capacity: float,
    execution_context: ExecutionContext,
) -> None:
    setup_logging(execution_context)

    scale_cluster(min_capacity, max_capacity)
    logger.info(
        "Cluster scaling initiated",
        min_capacity=min_capacity,
        max_capacity=max_capacity,
    )


def lambda_handler(event: dict, context: typing.Any) -> None:
    min_capacity = event["min_capacity"]
    max_capacity = event["max_capacity"]
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="graph_scaler",
    )
    handler(min_capacity, max_capacity, execution_context)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--min-capacity",
        type=float,
        help="The minimum NCU capacity of the serverless cluster.",
        required=True,
    )
    parser.add_argument(
        "--max-capacity",
        type=float,
        help="The maximum NCU capacity of the serverless cluster.",
        required=True,
    )

    args = parser.parse_args()

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_scaler",
    )
    handler(args.min_capacity, args.max_capacity, execution_context)


if __name__ == "__main__":
    local_handler()
