#!/usr/bin/env python
"""Work-discovery step for the id-minter state machine.

Queries `works-source` for the ids indexed within the scheduled window and
partitions them into `StepFunctionMintingRequest`s, one per minting Lambda
invocation fanned out by the state machine's Map. The event's pipeline_date
selects the ES secrets and the default index date; the `ES_SOURCE_INDEX_*` env
vars can override the index name, matching the minter itself.
"""

from __future__ import annotations

import os
import typing
from argparse import ArgumentParser
from itertools import batched

import structlog

from core.find_work import normalise_lambda_input, write_partitions_to_s3
from id_minter.config import IdMinterConfig
from id_minter.id_minting_source import MintingWorkIdsSource
from id_minter.models.step_events import (
    DEFAULT_MINT_PARTITION_SIZE,
    MintingFindWorkEvent,
    MintingFindWorkResult,
    StepFunctionMintingRequest,
)
from models.find_work import FindWorkRefsResult
from utils.argparse import add_pipeline_event_args
from utils.elasticsearch import ElasticsearchMode, get_client
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import create_job_id

logger = structlog.get_logger(__name__)


def handler(
    event: MintingFindWorkEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
) -> MintingFindWorkResult:
    setup_logging(execution_context)

    # The event's pipeline_date wins over the PIPELINE_DATE env default, so a
    # local --pipeline-date run reads the right secrets and index.
    cfg = IdMinterConfig(pipeline_date=event.pipeline_date)
    es_client = get_client(
        api_key_name="id_minter",
        pipeline_date=cfg.pipeline_date,
        es_mode=es_mode,
    )

    ids = list(
        MintingWorkIdsSource(event, es_client, cfg.source_index_name).stream_raw()
    )

    # Distinct per-partition job ids: concurrent partitions would otherwise
    # collide on their S3 report names (generated ids are minute-granular).
    base_job_id = event.job_id or create_job_id()
    partitions = [
        StepFunctionMintingRequest(
            source_identifiers=list(chunk),
            job_id=f"{base_job_id}-p{index:03d}",
        )
        for index, chunk in enumerate(batched(ids, event.partition_size))
    ]

    logger.info(
        "Found work",
        mode=event.mode_label,
        work_count=len(ids),
        partition_count=len(partitions),
        partition_size=event.partition_size,
        job_id=base_job_id,
    )
    return MintingFindWorkResult(partitions=partitions)


def lambda_handler(event: dict, context: typing.Any) -> dict[str, typing.Any]:
    # Scope comes from the invocation (scheduled_time or replay input); the
    # deployment-identity fields come from the environment.
    parsed_event = MintingFindWorkEvent(
        **normalise_lambda_input(
            event,
            defaults={
                "pipeline_date": os.environ.get("PIPELINE_DATE"),
                "graph_date": os.environ.get("GRAPH_DATE"),
            },
        )
    )
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="id_minter_find_work",
    )
    result = handler(parsed_event, execution_context)

    refs = write_partitions_to_s3(
        result.partitions,
        [len(p.source_identifiers or []) for p in result.partitions],
        parsed_event,
    )
    return FindWorkRefsResult(partitions=refs).model_dump(mode="json")


def local_handler(parser: ArgumentParser) -> None:
    add_pipeline_event_args(
        parser,
        {
            "pipeline_date",
            "window",
            "ids",
            "graph_date",
            "es_mode",
        },
    )
    parser.add_argument(
        "--partition-size",
        type=int,
        default=DEFAULT_MINT_PARTITION_SIZE,
        help="Number of work ids per minting Lambda invocation.",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        required=False,
        help="Base job id; each partition gets a -pNNN suffix.",
    )
    args = parser.parse_args()
    event = MintingFindWorkEvent.from_argparser(args)
    result = handler(event, es_mode=args.es_mode)
    print(result.model_dump_json())


if __name__ == "__main__":
    main_parser: ArgumentParser = ArgumentParser()
    local_handler(main_parser)
