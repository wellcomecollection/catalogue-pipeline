#!/usr/bin/env python
"""ID Minter step — Lambda and CLI entry points.

Follows the runtime / handler pattern used by the EBSCO adapter loader.
"""

from __future__ import annotations

import argparse
from datetime import datetime
from typing import Any

import structlog
from pydantic import BaseModel

from id_minter.config import ID_MINTER_CONFIG, IdMinterConfig
from id_minter.database import apply_migrations
from id_minter.models.step_events import (
    StepFunctionMintingRequest,
    StepFunctionMintingResponse,
)
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


# ---------------------------------------------------------------------------
# Runtime — injectable dependencies (mirrors LoaderRuntime pattern)
# ---------------------------------------------------------------------------
class IdMinterRuntime(BaseModel):
    """Container for runtime dependencies.

    Populated by ``build_runtime``; can be replaced in tests.
    """

    config: IdMinterConfig = ID_MINTER_CONFIG


def build_runtime(
    config_obj: IdMinterConfig | None = None,
) -> IdMinterRuntime:
    """Construct the runtime from configuration."""
    cfg = config_obj or ID_MINTER_CONFIG
    return IdMinterRuntime(config=cfg)


# ---------------------------------------------------------------------------
# Core logic
# ---------------------------------------------------------------------------
def execute(
    request: StepFunctionMintingRequest,
    runtime: IdMinterRuntime | None = None,
) -> StepFunctionMintingResponse:
    """Process a minting request and return a response.

    This is the pure-logic entry point, free of Lambda/logging concerns.
    """
    runtime = runtime or build_runtime()

    if runtime.config.apply_migrations:
        logger.info("Applying database migrations")
        apply_migrations(runtime.config)

    logger.info(
        "Processing minting request",
        job_id=request.job_id,
        source_identifier_count=len(request.source_identifiers),
        source_index=runtime.config.source_index,
        target_index=runtime.config.target_index,
    )

    # TODO: Implement minting logic:
    #  1. Fetch records from upstream ES (runtime.config.source_index)
    #  2. Mint canonical IDs via RDS identifier table
    #  3. Store minted records in downstream ES (runtime.config.target_index)
    #  4. Notify downstream SNS topic

    return StepFunctionMintingResponse(
        successes=[],
        failures=[],
        job_id=request.job_id,
    )


# ---------------------------------------------------------------------------
# Handler (adds logging / reporting around execute)
# ---------------------------------------------------------------------------
def handler(
    event: StepFunctionMintingRequest,
    runtime: IdMinterRuntime | None = None,
    execution_context: ExecutionContext | None = None,
) -> StepFunctionMintingResponse:
    setup_logging(execution_context)
    response = execute(event, runtime=runtime)

    logger.info(
        "Minting complete",
        job_id=response.job_id,
        success_count=len(response.successes),
        failure_count=len(response.failures),
    )

    return response


# ---------------------------------------------------------------------------
# Lambda entry point
# ---------------------------------------------------------------------------
def lambda_handler(event: dict, context: Any) -> dict[str, Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="id_minter",
    )
    request = StepFunctionMintingRequest.model_validate(event)
    runtime = build_runtime()
    response = handler(request, runtime=runtime, execution_context=execution_context)
    return response.model_dump()


# ---------------------------------------------------------------------------
# Local CLI entry point
# ---------------------------------------------------------------------------
def local_handler(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--source-identifiers",
        nargs="+",
        required=True,
        help="One or more source identifiers to mint.",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        required=False,
        help="Optional job ID (defaults to current time if omitted).",
    )
    parser.add_argument(
        "--source-index",
        type=str,
        required=False,
        help="Override the upstream ES index name.",
    )
    parser.add_argument(
        "--target-index",
        type=str,
        required=False,
        help="Override the downstream ES index name.",
    )
    parser.add_argument(
        "--apply-migrations",
        action="store_true",
        default=False,
        help="Apply database migrations before running.",
    )

    args = parser.parse_args()

    job_id = args.job_id or datetime.now().strftime("%Y%m%dT%H%M")
    request = StepFunctionMintingRequest(
        source_identifiers=args.source_identifiers,
        job_id=job_id,
    )

    overrides: dict = {}
    if args.source_index:
        overrides["source_index"] = args.source_index
    if args.target_index:
        overrides["target_index"] = args.target_index
    if args.apply_migrations:
        overrides["apply_migrations"] = True

    config_obj = IdMinterConfig(**overrides) if overrides else None
    runtime = build_runtime(config_obj)

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="id_minter",
    )

    response = handler(
        event=request,
        runtime=runtime,
        execution_context=execution_context,
    )
    logger.info(
        "ID minter run complete",
        response=response.model_dump(mode="json"),
    )


if __name__ == "__main__":
    local_handler(argparse.ArgumentParser(description="Run the id_minter locally."))
