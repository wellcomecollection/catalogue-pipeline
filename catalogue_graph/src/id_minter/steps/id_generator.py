#!/usr/bin/env python
"""
ID Generator step — Lambda and CLI entry points.

Pre-generates canonical IDs to ensure a pool of free IDs is always available
for the id_minter to assign.
"""

import argparse
import typing

import structlog
from pydantic import BaseModel

from id_minter.config import ID_GENERATOR_CONFIG, IdGeneratorConfig
from id_minter.database import apply_migrations, get_connection
from id_minter.pregenerate import top_up_ids
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


class IdGeneratorRuntime(BaseModel):
    """Container for runtime dependencies.

    Populated by ``build_runtime``; can be replaced in tests.
    """

    config: IdGeneratorConfig = ID_GENERATOR_CONFIG


def build_runtime(
    config_obj: IdGeneratorConfig | None = None,
) -> IdGeneratorRuntime:
    """Construct the runtime from configuration."""
    cfg = config_obj or ID_GENERATOR_CONFIG

    return IdGeneratorRuntime(config=cfg)


def handler(
    runtime: IdGeneratorRuntime, execution_context: ExecutionContext | None = None
) -> dict:
    setup_logging(execution_context)

    if runtime.config.apply_migrations:
        logger.info("Applying database migrations")
        apply_migrations(runtime.config)

    top_up_ids(
        conn=get_connection(runtime.config),
        desired_count=runtime.config.desired_free_ids_count,
    )
    return {"status": "success"}


def lambda_handler(event: dict, context: typing.Any) -> dict:
    runtime = build_runtime()
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="id_generator",
    )
    return handler(runtime, execution_context)


def local_handler(parser: argparse.ArgumentParser) -> None:
    """Local development entry point."""
    parser.add_argument(
        "--apply-migrations",
        action="store_true",
        default=False,
        help="Apply database migrations before running.",
    )
    parser.add_argument(
        "--desired-free-ids-count",
        type=int,
        default=ID_GENERATOR_CONFIG.desired_free_ids_count,
        help="Number of free IDs to maintain in the pool.",
    )

    args = parser.parse_args()

    overrides: dict = {}
    if args.apply_migrations:
        overrides["apply_migrations"] = True
    if args.desired_free_ids_count is not None:
        overrides["desired_free_ids_count"] = args.desired_free_ids_count

    config_obj = IdGeneratorConfig(**overrides) if overrides else None
    runtime = build_runtime(config_obj)

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="id_generator",
    )

    handler(runtime, execution_context)


if __name__ == "__main__":
    local_handler(argparse.ArgumentParser(description="Run the id_generator locally."))
