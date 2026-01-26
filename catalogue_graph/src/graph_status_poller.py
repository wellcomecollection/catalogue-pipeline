import typing

import boto3
import structlog

import config
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def check_cluster_status() -> str:
    client = boto3.client("neptune")
    response = client.describe_db_clusters(
        DBClusterIdentifier=config.NEPTUNE_CLUSTER_IDENTIFIER,
    )

    cluster_info = response["DBClusters"][0]
    status: str = cluster_info["Status"]
    return status


def handler(execution_context: ExecutionContext) -> dict:
    setup_logging(execution_context)

    status = check_cluster_status()
    logger.info("Cluster status checked", status=status)
    return {"status": status}


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="graph_status_poller",
    )
    return handler(execution_context)


if __name__ == "__main__":
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_status_poller",
    )
    result = handler(execution_context)
    logger.info("Status poll complete", status=result["status"])
