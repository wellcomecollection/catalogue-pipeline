#!/usr/bin/env python
"""ID Minter step — Lambda and CLI entry points.

Follows the runtime / handler pattern used by the EBSCO adapter loader.
"""

from __future__ import annotations

import argparse
from datetime import datetime
from typing import Any

import structlog
from pydantic import BaseModel, ConfigDict

from id_minter.config import ID_MINTER_CONFIG, IdMinterConfig
from id_minter.database import apply_migrations
from id_minter.id_minting_transformer import IdMintingTransformer
from id_minter.models.identifier import IdResolver
from id_minter.models.step_events import (
    StepFunctionMintingFailure,
    StepFunctionMintingRequest,
    StepFunctionMintingResponse,
)
from id_minter.resolvers.data_api_resolver import DataApiIdResolver
from id_minter.resolvers.lookup_resolver import LookupOnlyIdResolver
from utils.elasticsearch import ElasticsearchMode, get_client
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


class IdMinterRuntime(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)

    config: IdMinterConfig = ID_MINTER_CONFIG
    resolver: IdResolver
    source_es_mode: ElasticsearchMode = "private"
    target_es_mode: ElasticsearchMode = "private"


def build_runtime(
    config_obj: IdMinterConfig | None = None,
    resolver: IdResolver | None = None,
    source_es_mode: ElasticsearchMode = "private",
    target_es_mode: ElasticsearchMode = "private",
) -> IdMinterRuntime:
    cfg = config_obj or ID_MINTER_CONFIG
    res = resolver or LookupOnlyIdResolver(cfg)
    return IdMinterRuntime(
        config=cfg,
        resolver=res,
        source_es_mode=source_es_mode,
        target_es_mode=target_es_mode,
    )


def execute(
    request: StepFunctionMintingRequest,
    runtime: IdMinterRuntime,
) -> StepFunctionMintingResponse:
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

    source_index = f"{runtime.config.source_index}-{runtime.config.pipeline_date}"
    target_index = f"{runtime.config.target_index}-{runtime.config.pipeline_date}"

    source_client = get_client(
        api_key_name="id_minter",
        pipeline_date=runtime.config.pipeline_date,
        es_mode=runtime.source_es_mode,
    )
    target_client = get_client(
        api_key_name="id_minter",
        pipeline_date=runtime.config.pipeline_date,
        es_mode=runtime.target_es_mode,
    )

    transformer = IdMintingTransformer(
        es_client=source_client,
        source_index=source_index,
        source_identifiers=request.source_identifiers,
        resolver=runtime.resolver,
    )
    transformer.stream_to_index(target_client, target_index)

    failures = [
        StepFunctionMintingFailure(
            source_identifier=error.row_id,
            error=error.detail,
        )
        for error in transformer.errors
    ]

    return StepFunctionMintingResponse(
        successes=transformer.successful_ids,
        failures=failures,
        job_id=request.job_id,
    )


def handler(
    event: StepFunctionMintingRequest,
    runtime: IdMinterRuntime,
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


def lambda_handler(event: dict, context: Any) -> dict[str, Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="id_minter",
    )
    request = StepFunctionMintingRequest.model_validate(event)
    runtime = build_runtime()
    response = handler(
        request,
        runtime=runtime,
        execution_context=execution_context,
    )
    return response.model_dump()


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
    parser.add_argument(
        "--resolver",
        choices=["local", "data-api"],
        default="local",
        help="ID resolver backend: 'local' (pymysql to local MySQL) "
        "or 'data-api' (AWS RDS Data API). Default: local.",
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        required=False,
        help="Override the pipeline date (used for ES index suffixes and secret names).",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        default=False,
        help="Print the resolved configuration and exit without running.",
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
    if args.pipeline_date:
        overrides["pipeline_date"] = args.pipeline_date

    config_obj = IdMinterConfig(**overrides) if overrides else None
    cfg = config_obj or ID_MINTER_CONFIG

    resolver: IdResolver
    if args.resolver == "data-api":
        resolver = DataApiIdResolver(cfg)
    else:
        resolver = LookupOnlyIdResolver(cfg)

    runtime = build_runtime(
        config_obj,
        resolver=resolver,
        source_es_mode="public",
        target_es_mode="local",
    )

    if args.dry_run:
        source_index = f"{cfg.source_index}-{cfg.pipeline_date}"
        target_index = f"{cfg.target_index}-{cfg.pipeline_date}"
        resolver_name = "data-api" if args.resolver == "data-api" else "local"
        db_host = (
            f"{cfg.rds_cluster_id} ({cfg.rds_region})"
            if resolver_name == "data-api"
            else f"{cfg.rds_client.primary_host}:{cfg.rds_client.port}"
        )
        print(  # noqa: T201
            f"\n  Resolver:        {resolver_name}\n"
            f"  Database:        {cfg.db_name} @ {db_host}\n"
            f"  Pipeline date:   {cfg.pipeline_date}\n"
            f"  Source ES:       {runtime.source_es_mode} → {source_index}\n"
            f"  Target ES:       {runtime.target_es_mode} → {target_index}\n"
            f"  Identifiers:     {request.source_identifiers}\n"
            f"  Migrations:      {'yes' if cfg.apply_migrations else 'no'}\n"
        )
        return

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
