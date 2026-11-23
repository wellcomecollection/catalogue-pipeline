"""Prunes superseded rows from the works_identified table and reports its size."""

import argparse
import os
import typing

import structlog

from id_minter.iceberg import get_works_identified_table
from id_minter.pruner import PruneResult, prune
from id_minter.reporting import PrunerReport
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

PIPELINE_DATE = os.getenv("PIPELINE_DATE", "dev")


def handler(
    execution_context: ExecutionContext | None = None,
    use_rest_api_table: bool = True,
) -> PruneResult:
    setup_logging(execution_context)

    table = get_works_identified_table(use_rest_api_table)
    result = prune(table)
    PrunerReport(
        pipeline_date=PIPELINE_DATE,
        row_count=result.row_count,
        superseded_row_count=result.superseded_row_count,
        publish_to_s3=False,
    ).publish()
    return result


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=PrunerReport.label,
    )
    return handler(execution_context).model_dump(mode="json")


def local_handler() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Prune the S3 Tables table named by WORKS_IDENTIFIED_TABLE_NAME instead of the local one.",
    )
    args = parser.parse_args()

    result = handler(use_rest_api_table=args.use_rest_api_table)
    logger.info("Pruner finished", **result.model_dump())


if __name__ == "__main__":
    local_handler()
