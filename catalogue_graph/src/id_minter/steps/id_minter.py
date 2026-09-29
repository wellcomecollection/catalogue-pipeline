#!/usr/bin/env python
"""ID Minter step — Lambda and CLI entry points.

Follows the runtime / handler pattern used by the EBSCO adapter loader.
"""

from __future__ import annotations

import argparse
from typing import Any, Literal

import structlog
from pydantic import BaseModel, ConfigDict
from pyiceberg.table import Table as IcebergTable

from core.sinks import ElasticsearchSink, Sink
from core.transformer import SinkResult
from id_minter.config import ID_MINTER_CONFIG, IdMinterConfig
from id_minter.database import apply_migrations
from id_minter.iceberg import IcebergSink, get_works_identified_table
from id_minter.id_minting_source import IdMintingSource
from id_minter.id_minting_transformer import IdMintingTransformer
from id_minter.models.identifier import IdResolver
from id_minter.models.step_events import (
    StepFunctionMintingRequest,
)
from id_minter.reporting import IdMinterReport
from id_minter.resolvers.data_api_resolver import DataApiIdResolver
from id_minter.resolvers.minting_resolver import MintingResolver
from id_minter.sns import publish_ids_to_sns
from models.incremental_window import IncrementalWindow
from utils.aws import pydantic_from_s3_json
from utils.elasticsearch import ElasticsearchMode, get_client
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import create_job_id

logger = structlog.get_logger(__name__)


class IdMinterRuntime(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)

    config: IdMinterConfig = ID_MINTER_CONFIG
    resolver: IdResolver
    source_es_mode: ElasticsearchMode = "private"
    target_es_mode: ElasticsearchMode = "private"
    iceberg_table: IcebergTable | None = None


class IdMinterResult(BaseModel):
    """Run summary. Deliberately does not echo source_identifiers: a full
    partition's id list would blow the Step Functions 256 KB task-output limit."""

    job_id: str
    window: IncrementalWindow | None = None
    success_count: int
    superseded_count: int = 0
    failure_count: int
    report_s3_uri: str


def build_runtime(
    config_obj: IdMinterConfig | None = None,
    resolver: IdResolver | None = None,
    source_es_mode: ElasticsearchMode = "private",
    target_es_mode: ElasticsearchMode = "private",
    iceberg_table_mode: Literal["rest", "local"] = "rest",
) -> IdMinterRuntime:
    cfg = config_obj or ID_MINTER_CONFIG
    res = resolver or MintingResolver(cfg)
    iceberg_table = None
    if cfg.enable_iceberg_writes:
        iceberg_table = get_works_identified_table(
            use_rest_api_table=iceberg_table_mode == "rest", create_if_not_exists=True
        )

    return IdMinterRuntime(
        config=cfg,
        resolver=res,
        source_es_mode=source_es_mode,
        target_es_mode=target_es_mode,
        iceberg_table=iceberg_table,
    )


def execute(
    request: StepFunctionMintingRequest,
    runtime: IdMinterRuntime,
) -> SinkResult:
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

    elastic_source = IdMintingSource(
        source_scope=request.source_scope,
        es_client=source_client,
        index_name=source_index,
    )
    transformer = IdMintingTransformer(elastic_source, resolver=runtime.resolver)

    # Documents also go to an experimental Iceberg table when enabled.
    sinks: list[Sink] = [ElasticsearchSink(target_client, target_index)]
    if runtime.iceberg_table is not None:
        sinks.append(IcebergSink(runtime.iceberg_table))

    result, *_ = transformer.stream_to_many(*sinks)

    # Superseded works are forwarded too: the matcher reads the newer copy, and a run
    # that died between indexing and publishing is covered by the next one.
    ids_to_publish = result.accepted_ids + result.superseded_ids
    if runtime.config.downstream_sns_topic_arn and ids_to_publish:
        publish_ids_to_sns(runtime.config.downstream_sns_topic_arn, ids_to_publish)

    return result


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
        iceberg_writes="yes" if cfg.enable_iceberg_writes else "no",
        mode=request.source_scope.mode_label,
        identifiers=request.source_identifiers,
        window=request.window.model_dump() if request.window else None,
        migrations="yes" if cfg.apply_migrations else "no",
    )


def handler(
    event: StepFunctionMintingRequest,
    runtime: IdMinterRuntime,
    execution_context: ExecutionContext | None = None,
) -> IdMinterResult:
    setup_logging(execution_context)
    log_runtime_config(runtime, event)
    result = execute(event, runtime=runtime)

    logger.info(
        "Minting complete",
        job_id=event.job_id,
        success_count=len(result.accepted_ids),
        superseded_count=len(result.superseded_ids),
        failure_count=len(result.errors),
    )

    report = IdMinterReport(
        pipeline_date=runtime.config.pipeline_date,
        job_id=event.job_id,
        successful_ids=result.accepted_ids,
        superseded_ids=result.superseded_ids,
        errors=result.errors,
        s3_bucket=runtime.config.s3_bucket,
        s3_prefix=runtime.config.s3_prefix,
    )
    report.publish()

    return IdMinterResult.model_validate(
        {
            **event.model_dump(),
            "success_count": len(result.accepted_ids),
            "superseded_count": len(result.superseded_ids),
            "failure_count": len(result.errors),
            "report_s3_uri": report.s3_uri,
        }
    )


def lambda_handler(event: dict, context: Any) -> dict[str, Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="id_minter",
    )
    if "s3_uri" in event:
        # A partition ref from the find-work step; resolve the full request
        # (ids + per-partition job_id) from S3.
        request = pydantic_from_s3_json(StepFunctionMintingRequest, event["s3_uri"])
    else:
        if "job_id" not in event:
            event["job_id"] = create_job_id()
        request = StepFunctionMintingRequest.model_validate(event)
        # Neither ids nor window means a full-index mint; require an explicit
        # opt-in so a mistyped invoke (e.g. "s3Uri") fails loudly instead.
        if (
            request.source_identifiers is None
            and request.window is None
            and event.get("full") is not True
        ):
            raise ValueError(
                "Neither source_identifiers nor window given; "
                "pass 'full': true to mint the entire index."
            )
    runtime = build_runtime()
    response = handler(
        request,
        runtime=runtime,
        execution_context=execution_context,
    )
    return response.model_dump(mode="json")


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
    parser.add_argument(
        "--enable-iceberg-writes",
        action="store_true",
        default=False,
        help="Also append the indexed documents to the works identified Iceberg table.",
    )
    parser.add_argument(
        "--iceberg-table-mode",
        choices=["rest", "local"],
        default="local",
        help="Iceberg catalog for --enable-iceberg-writes: S3 Tables or the local one. Default: local.",
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
    if args.enable_iceberg_writes:
        overrides["enable_iceberg_writes"] = True

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
        iceberg_table_mode=args.iceberg_table_mode,
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
