#!/usr/bin/env python
"""ID Minter step — Lambda and CLI entry points.

Follows the runtime / handler pattern used by the EBSCO adapter loader.
"""

from __future__ import annotations

import argparse
from typing import Any

import structlog
from elasticsearch import Elasticsearch
from pydantic import BaseModel, ConfigDict

from id_minter.config import ID_MINTER_CONFIG, IdMinterConfig
from id_minter.database import apply_migrations
from id_minter.id_minting_source import IdMintingSource
from id_minter.id_minting_transformer import IdMintingTransformer
from id_minter.manifests import IdMinterManifestWriter
from id_minter.models.identifier import IdResolver
from id_minter.models.step_events import (
    StepFunctionMintingRequest,
)
from id_minter.reporting import IdMinterReport
from id_minter.resolvers.data_api_resolver import DataApiIdResolver
from id_minter.resolvers.minting_resolver import MintingResolver
from id_minter.sns import publish_ids_to_sns
from models.incremental_window import IncrementalWindow
from models.source_scope import SourceScope
from utils.elasticsearch import ElasticsearchMode, get_client
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.models.manifests import StepManifest
from utils.steps import create_job_id

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
    res = resolver or MintingResolver(cfg)
    return IdMinterRuntime(
        config=cfg,
        resolver=res,
        source_es_mode=source_es_mode,
        target_es_mode=target_es_mode,
    )


def build_minting_source(
    source_scope: SourceScope,
    es_client: Elasticsearch,
    index_name: str,
) -> IdMintingSource:
    full_query = source_scope.to_elasticsearch_query(
        range_filter_field_name="indexed_at"
    )

    return IdMintingSource(
        es_client=es_client,
        index_name=index_name,
        query=full_query,
        slice_count=source_scope.slice_count,
    )


def execute(
    request: StepFunctionMintingRequest,
    runtime: IdMinterRuntime,
) -> StepManifest:
    if runtime.config.apply_migrations:
        logger.info("Applying database migrations")
        apply_migrations(runtime.config)

    logger.info(
        "Processing minting request",
        job_id=request.job_id,
        source_identifier_count=len(request.source_identifiers)
        if request.source_identifiers
        else None,
        window=request.window.model_dump() if request.window else None,
        source_index_prefix=runtime.config.source_index_prefix,
        target_index_prefix=runtime.config.target_index_prefix,
    )

    source_index = runtime.config.source_index_name
    target_index = runtime.config.target_index_name

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

    elastic_source = build_minting_source(
        request.source_scope, source_client, source_index
    )
    transformer = IdMintingTransformer(
        elastic_source,
        resolver=runtime.resolver,
    )
    transformer.stream_to_index(target_client, target_index)

    if runtime.config.downstream_sns_topic_arn and transformer.successful_ids:
        publish_ids_to_sns(
            runtime.config.downstream_sns_topic_arn,
            transformer.successful_ids,
        )

    manifest_writer = IdMinterManifestWriter(
        job_id=request.job_id,
        label="id_minter",
        bucket=runtime.config.s3_bucket,
        prefix=runtime.config.batch_s3_prefix,
    )

    return manifest_writer.build_manifest(
        successful_ids=transformer.successful_ids,
        errors=transformer.errors,
    )


def log_runtime_config(
    runtime: IdMinterRuntime,
    request: StepFunctionMintingRequest,
) -> None:
    cfg = runtime.config
    resolver_name = type(runtime.resolver).__name__
    db_host = (
        f"{cfg.rds_cluster_id} ({cfg.rds_region})"
        if isinstance(runtime.resolver, DataApiIdResolver)
        else f"{cfg.rds_client.primary_host}:{cfg.rds_client.port}"
    )
    source_date = cfg.source_index_date_suffix or cfg.pipeline_date
    target_date = cfg.target_index_date_suffix or cfg.pipeline_date
    date_info = cfg.pipeline_date
    if source_date != cfg.pipeline_date or target_date != cfg.pipeline_date:
        date_info += f" (source: {source_date}, target: {target_date})"

    logger.info(
        "Runtime configuration",
        resolver=resolver_name,
        database=f"{cfg.db_name} @ {db_host}",
        pipeline_date=date_info,
        source_es=f"{runtime.source_es_mode} → {cfg.source_index_name}",
        target_es=f"{runtime.target_es_mode} → {cfg.target_index_name}",
        downstream_sns=cfg.downstream_sns_topic_arn or "disabled",
        mode=request.source_scope.mode_label,
        identifiers=request.source_identifiers,
        window=request.window.model_dump() if request.window else None,
        migrations="yes" if cfg.apply_migrations else "no",
    )


def handler(
    event: StepFunctionMintingRequest,
    runtime: IdMinterRuntime,
    execution_context: ExecutionContext | None = None,
) -> StepManifest:
    setup_logging(execution_context)
    log_runtime_config(runtime, event)
    response = execute(event, runtime=runtime)

    success_count = response.successes.count
    failure_count = response.failures.count if response.failures else 0

    logger.info(
        "Minting complete",
        job_id=response.job_id,
        success_count=success_count,
        failure_count=failure_count,
    )

    try:
        IdMinterReport(
            pipeline_date=runtime.config.pipeline_date,
            success_count=success_count,
            failure_count=failure_count,
        ).publish()
    # The metric triggers an alarm if failure_count > 0
    # If publish fails AND we have failure, we raise
    except Exception as e:
        logger.warning("Failed to publish metrics", exc_info=True)
        if failure_count > 0:
            raise RuntimeError(
                f"Failed to publish metrics for a run with {failure_count} failures: {response.job_id}"
            ) from e
    return response


def lambda_handler(event: dict, context: Any) -> dict[str, Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="id_minter",
    )
    if "job_id" not in event:
        event["job_id"] = create_job_id()
    request = StepFunctionMintingRequest.model_validate(event)
    runtime = build_runtime()
    response = handler(
        request,
        runtime=runtime,
        execution_context=execution_context,
    )
    return response.model_dump()


def local_handler(parser: argparse.ArgumentParser) -> None:
    # -- Source selection (mutually exclusive: ids, window, or neither for full) --
    parser.add_argument(
        "--source-identifiers",
        nargs="+",
        required=False,
        default=None,
        help="One or more source identifiers to mint (IDs mode).",
    )
    parser.add_argument(
        "--window-end",
        type=str,
        required=False,
        default=None,
        help="End of the time window (ISO 8601). Window mode.",
    )
    parser.add_argument(
        "--window-start",
        type=str,
        required=False,
        default=None,
        help="Start of the time window (ISO 8601). Defaults to end_time - 15 minutes.",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        required=False,
        help="Optional job ID (defaults to current time if omitted).",
    )
    parser.add_argument(
        "--source-index-prefix",
        type=str,
        required=False,
        help="Override the upstream ES index name prefix.",
    )
    parser.add_argument(
        "--target-index-prefix",
        type=str,
        required=False,
        help="Override the downstream ES index name prefix.",
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
        default="data-api",
        help="ID resolver backend: 'local' (pymysql to local MySQL) "
        "or 'data-api' (AWS RDS Data API). Default: data-api.",
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        required=False,
        help="Override the pipeline date (used for ES secrets and as default for index suffixes).",
    )
    parser.add_argument(
        "--source-index-date-suffix",
        type=str,
        required=False,
        help="Override the date suffix for the source index. Defaults to --pipeline-date.",
    )
    parser.add_argument(
        "--target-index-date-suffix",
        type=str,
        required=False,
        help="Override the date suffix for the target index. Defaults to --pipeline-date.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        default=False,
        help="Print the resolved configuration and exit without running.",
    )
    parser.add_argument(
        "--source-es-mode",
        choices=["public", "private", "local"],
        default="public",
        help="Elasticsearch mode for reading source documents. Default: public.",
    )
    parser.add_argument(
        "--target-es-mode",
        choices=["public", "private", "local"],
        default="local",
        help="Elasticsearch mode for writing indexed documents. Default: local.",
    )

    args = parser.parse_args()

    job_id = args.job_id or create_job_id()
    request = StepFunctionMintingRequest(
        source_identifiers=args.source_identifiers,
        window=IncrementalWindow.from_argparser(args),
        job_id=job_id,
    )

    overrides: dict = {}
    if args.source_index_prefix:
        overrides["source_index_prefix"] = args.source_index_prefix
    if args.target_index_prefix:
        overrides["target_index_prefix"] = args.target_index_prefix
    if args.apply_migrations:
        overrides["apply_migrations"] = True
    if args.pipeline_date:
        overrides["pipeline_date"] = args.pipeline_date
    if args.source_index_date_suffix:
        overrides["source_index_date_suffix"] = args.source_index_date_suffix
    if args.target_index_date_suffix:
        overrides["target_index_date_suffix"] = args.target_index_date_suffix

    config_obj = IdMinterConfig(**overrides) if overrides else None
    cfg = config_obj or ID_MINTER_CONFIG

    resolver: IdResolver
    if args.resolver == "data-api":
        resolver = DataApiIdResolver(cfg)
    else:
        resolver = MintingResolver(cfg)

    runtime = build_runtime(
        config_obj,
        resolver=resolver,
        source_es_mode=args.source_es_mode,
        target_es_mode=args.target_es_mode,
    )

    if args.dry_run:
        log_runtime_config(runtime, request)
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
