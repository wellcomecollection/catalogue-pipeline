"""Generic loader step for OAI-PMH adapters.

The loader runs in one of two modes, selected by the event's ``mode`` field.

Window mode harvests the range requested by the trigger step, tracking progress
in the window store so an interrupted run resumes where it stopped. Id mode
fetches an explicit list of record ids via ``GetRecord``, for records the source
holds but the store is missing; it keeps no window state, so a re-run re-fetches
everything.

The modes share only the record writer. Both emit changeset identifiers, so
either causes the state machine to publish and the transformer to pick the work
up.

This module provides reusable loader logic that can be used by any
OAI-PMH adapter by providing an OAIPMHRuntimeConfig implementation.
"""

from __future__ import annotations

import argparse
import json
import time
from typing import Any

import httpx
import structlog
from lxml import etree
from oai_pmh_client.client import OAIClient
from oai_pmh_client.exceptions import IdDoesNotExistError, OAIError
from pydantic import BaseModel, ConfigDict

from adapters.extractors.oai_pmh.models.step_events import (
    UNFETCHABLE_SAMPLE_SIZE,
    LoaderEvent,
    LoaderResponse,
    OAIPMHIdLoaderEvent,
    OAIPMHIdLoaderResponse,
    OAIPMHLoaderEvent,
    OAIPMHLoaderResponse,
    validate_loader_event,
)
from adapters.extractors.oai_pmh.record_writer import (
    BufferedWindowRecordWriter,
    WindowRecordWriter,
)
from adapters.extractors.oai_pmh.registry import get_config
from adapters.extractors.oai_pmh.reporting import OAIPMHIdLoadReport, OAIPMHLoaderReport
from adapters.extractors.oai_pmh.runtime import OAIPMHRuntimeConfig
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_generator import WindowGenerator
from adapters.utils.window_harvester import WindowHarvestManager
from adapters.utils.window_store import WindowStore
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import create_job_id, ecs_handler

logger = structlog.get_logger(__name__)


class LoaderStepConfig(BaseModel):
    """Configuration for the loader step runtime."""

    use_rest_api_table: bool = True
    window_minutes: int | None = None
    allow_partial_final_window: bool = False
    suppress_summaries: bool = True

    reprocess_successful_windows: bool = False
    """Re-harvest windows already marked successful (backfill mode)."""

    flush_every: int | None = None
    """Batch record and window-status commits every N windows instead of per
    window. Intended for resumable backfills: a crash loses up to N windows of
    uncommitted work, which are re-harvested on the next run. None (default)
    keeps the classic one-commit-per-window behaviour."""


class LoaderRuntime(BaseModel):
    """Runtime dependencies for the loader step."""

    store: WindowStore
    table_client: AdapterStore
    oai_client: OAIClient
    window_generator: WindowGenerator
    adapter_namespace: str
    adapter_name: str
    suppress_summaries: bool = True
    report_s3_bucket: str | None = None
    report_s3_prefix: str = "dev"
    reprocess_successful_windows: bool = False
    flush_every: int | None = None

    model_config = ConfigDict(arbitrary_types_allowed=True)


def build_runtime(
    config: OAIPMHRuntimeConfig,
    step_config: LoaderStepConfig | None = None,
) -> LoaderRuntime:
    """Build the loader runtime from adapter configuration.

    Args:
        config: Adapter-specific runtime configuration.
        step_config: Optional step-specific overrides.

    Returns:
        LoaderRuntime ready for execution.
    """
    cfg = step_config or LoaderStepConfig()
    store = config.build_window_store(use_rest_api_table=cfg.use_rest_api_table)
    table_client = config.build_adapter_store(use_rest_api_table=cfg.use_rest_api_table)
    oai_client = config.build_oai_client()

    window_generator = WindowGenerator(
        window_minutes=cfg.window_minutes or config.config.window_minutes,
        allow_partial_final_window=cfg.allow_partial_final_window,
    )

    return LoaderRuntime(
        store=store,
        table_client=table_client,
        oai_client=oai_client,
        window_generator=window_generator,
        adapter_namespace=config.config.adapter_namespace,
        adapter_name=config.config.adapter_name,
        suppress_summaries=cfg.suppress_summaries,
        report_s3_bucket=config.config.report_s3_bucket,
        report_s3_prefix=config.config.report_s3_prefix,
        reprocess_successful_windows=cfg.reprocess_successful_windows,
        flush_every=cfg.flush_every,
    )


def build_harvester(
    request: OAIPMHLoaderEvent,
    runtime: LoaderRuntime,
) -> WindowHarvestManager:
    """Build a window harvester for the given request.

    Args:
        request: Loader event with window and OAI-PMH parameters.
        runtime: Loader runtime with clients and configuration.

    Returns:
        WindowHarvestManager configured for this request.
    """
    writer_cls = (
        BufferedWindowRecordWriter if runtime.flush_every else WindowRecordWriter
    )
    record_writer = writer_cls(
        namespace=runtime.adapter_namespace,
        table_client=runtime.table_client,
        job_id=request.job_id,
        window_range=request.window.to_iso_string(),
    )
    flush_callback = (
        record_writer.flush
        if isinstance(record_writer, BufferedWindowRecordWriter)
        else None
    )
    return WindowHarvestManager(
        store=runtime.store,
        window_generator=runtime.window_generator,
        client=runtime.oai_client,
        metadata_prefix=request.metadata_prefix,
        set_spec=request.set_spec,
        record_callback=record_writer,
        flush_callback=flush_callback,
    )


def execute_loader(
    request: OAIPMHLoaderEvent,
    runtime: LoaderRuntime,
) -> OAIPMHLoaderResponse:
    """Execute the loader step to harvest records.

    Args:
        request: Loader event with window and OAI-PMH parameters.
        runtime: Loader runtime with clients and configuration.

    Returns:
        OAIPMHLoaderResponse with harvest results (empty when nothing was pending).
    """
    window = request.window
    harvester = build_harvester(request, runtime)

    summaries = harvester.harvest_range(
        time_range=window,
        max_windows=request.max_windows,
        reprocess_successful_windows=runtime.reprocess_successful_windows,
        flush_every=runtime.flush_every,
    )

    if not summaries:
        # Every candidate window in the range was already harvested (or the range
        # produced none): a caught-up no-op, not an error. Return an empty response so
        # the state machine routes straight to success instead of failing and retrying.
        logger.info(
            "No pending windows to harvest",
            window_start=window.start_time.isoformat(),
            window_end=window.end_time.isoformat(),
        )

    # In buffered mode changeset ids and upserted counts are produced per
    # flush, not per window, so they aren't in the summaries' tags and must be
    # added to the response here.
    extra_changeset_ids: list[str] = []
    extra_upserted_record_count = 0
    if isinstance(harvester.record_callback, BufferedWindowRecordWriter):
        extra_changeset_ids = harvester.record_callback.changeset_ids
        extra_upserted_record_count = harvester.record_callback.upserted_record_count

    return OAIPMHLoaderResponse.from_summaries(
        summaries,
        job_id=request.job_id,
        extra_changeset_ids=extra_changeset_ids,
        extra_upserted_record_count=extra_upserted_record_count,
    )


class IdLoadOutcome:
    """Classification counts for an id-mode run.

    Ids fall into exactly one of three buckets. ``removed`` records the source
    reporting ``idDoesNotExist``; note this does *not* write a tombstone. See
    :func:`execute_id_loader` for why.
    """

    def __init__(self) -> None:
        self.recovered: list[str] = []
        self.removed: list[str] = []
        self.unfetchable: list[str] = []


def execute_id_loader(
    request: OAIPMHIdLoaderEvent,
    runtime: LoaderRuntime,
) -> tuple[OAIPMHIdLoaderResponse, list[str]]:
    """Fetch each requested id and write the recoverable ones in batches.

    Returns the response alongside the *full* list of unfetchable ids. The
    response carries only a sample of those, to bound the payload the state
    machine passes onward; the full list belongs in the report.

    Deletions are deliberately asymmetric with window mode. There, a tombstone
    is written when the repository affirmatively reports ``status="deleted"``.
    Here, ``IdDoesNotExistError`` is a weaker and different assertion -- "this
    identifier is not in this repository" -- which fires equally for a typo in
    an operator's id list or an id from another set. Since this response now
    publishes changesets, tombstoning on that signal would propagate a delete
    downstream to Elasticsearch automatically. Removed ids are therefore
    reported and counted, never written.

    No window-status rows are written either. A synthetic window row would enter
    the coverage report and shift the trigger's resume cursor to a range that
    was never harvested.
    """
    unique_ids = list(dict.fromkeys(i for i in request.ids if i))
    outcome = IdLoadOutcome()

    writer = BufferedWindowRecordWriter(
        namespace=runtime.adapter_namespace,
        table_client=runtime.table_client,
        job_id=request.job_id,
        flush_threshold=request.commit_every,
    )

    for record_id in unique_ids:
        time.sleep(request.polite_delay_seconds)
        try:
            record = runtime.oai_client.get_record(
                identifier=record_id, metadata_prefix=request.metadata_prefix
            )
        except IdDoesNotExistError:
            outcome.removed.append(record_id)
            continue
        except (etree.XMLSyntaxError, OAIError, httpx.HTTPError) as exc:
            # Empty body, an OAI protocol error, or a network failure after the
            # client's own retries. The source this targets is known to be
            # flaky, so classify the id as unfetchable and keep going rather than
            # aborting a large batch on one bad id. Unexpected exceptions (bugs)
            # are deliberately not caught here.
            logger.warning(
                "Unfetchable record",
                record_id=record_id,
                error=type(exc).__name__,
            )
            outcome.unfetchable.append(record_id)
            continue

        outcome.recovered.append(record_id)
        writer([(record_id, record)])

    writer.flush()

    logger.info(
        "Id load complete",
        adapter=runtime.adapter_name,
        requested=len(unique_ids),
        recovered=len(outcome.recovered),
        removed=len(outcome.removed),
        unfetchable=len(outcome.unfetchable),
        changeset_count=len(writer.changeset_ids),
    )

    response = OAIPMHIdLoaderResponse(
        job_id=request.job_id,
        changeset_ids=writer.changeset_ids,
        changed_record_count=writer.upserted_record_count,
        requested=len(unique_ids),
        recovered=len(outcome.recovered),
        removed=len(outcome.removed),
        unfetchable_count=len(outcome.unfetchable),
        unfetchable_sample=outcome.unfetchable[:UNFETCHABLE_SAMPLE_SIZE],
    )
    return response, outcome.unfetchable


def handler(
    event: LoaderEvent,
    runtime: LoaderRuntime,
    execution_context: ExecutionContext | None = None,
) -> LoaderResponse:
    """Execute the loader step in whichever mode the event selects.

    Args:
        event: Loader event, either window mode or id mode.
        runtime: Runtime dependencies and configuration.
        execution_context: Optional logging context.

    Returns:
        The response for the selected mode. Both carry ``changeset_ids``, which
        is what drives the downstream publish.
    """
    setup_logging(execution_context)

    if isinstance(event, OAIPMHIdLoaderEvent):
        id_response, unfetchable = execute_id_loader(event, runtime=runtime)
        OAIPMHIdLoadReport.from_id_load(
            id_response,
            adapter_type=runtime.adapter_name,
            unfetchable=unfetchable,
            report_s3_bucket=runtime.report_s3_bucket,
            report_s3_prefix=runtime.report_s3_prefix,
        ).publish()
        return id_response

    response = execute_loader(event, runtime=runtime)

    report = OAIPMHLoaderReport.from_loader(
        event,
        response,
        adapter_type=runtime.adapter_name,
        report_s3_bucket=runtime.report_s3_bucket,
        report_s3_prefix=runtime.report_s3_prefix,
    )
    report.publish()

    if runtime.suppress_summaries:
        response.summaries = []

    return response


def lambda_handler(
    event: dict[str, Any],
    context: Any,
) -> dict[str, Any]:
    """Unified Lambda entry point for OAI-PMH loader steps.

    Resolves the adapter config from the ``adapter_type`` field in the event
    (injected by the Step Functions state machine from the scheduler input).
    """
    request = validate_loader_event(event)
    config = get_config(request.adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=f"{config.config.pipeline_step_prefix}_loader",
    )
    runtime = build_runtime(config)
    response = handler(request, runtime, execution_context=execution_context)
    return response.model_dump(mode="json")


def _read_ids(args: argparse.Namespace) -> list[str]:
    """Collect record ids from --ids and/or --ids-file."""
    ids: list[str] = []
    if args.ids:
        ids += [i.strip() for i in args.ids.split(",") if i.strip()]
    if args.ids_file:
        with open(args.ids_file, encoding="utf-8") as f:
            ids += [line.strip() for line in f if line.strip()]
    if not ids:
        raise ValueError("No record ids supplied; use --ids or --ids-file")
    return ids


def local_handler(parser: argparse.ArgumentParser) -> None:
    """Run the loader step from the command line."""
    from adapters.utils.argparse import add_adapter_event_args

    add_adapter_event_args(parser)
    parser.add_argument(
        "--event",
        type=str,
        default=None,
        help="Path to a window-mode loader event JSON payload",
    )
    parser.add_argument(
        "--ids",
        type=str,
        default=None,
        help="Comma-separated record ids to fetch (selects id mode)",
    )
    parser.add_argument(
        "--ids-file",
        type=str,
        default=None,
        help="Path to a file of record ids, one per line (selects id mode)",
    )
    parser.add_argument(
        "--commit-every",
        type=int,
        default=None,
        metavar="N",
        help="Id mode: records buffered before committing a batch",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        default=None,
        help="Id mode: job identifier (defaults to a generated one)",
    )
    parser.add_argument(
        "--allow-partial-final-window",
        action="store_true",
        default=True,
        help="Allow partial final window (default: True for CLI)",
    )
    parser.add_argument(
        "--reprocess-successful-windows",
        action="store_true",
        help="Re-harvest windows already marked successful (backfill mode)",
    )
    parser.add_argument(
        "--flush-every",
        type=int,
        default=None,
        metavar="N",
        help=(
            "Batch record and window-status commits every N windows instead of "
            "per window (resumable backfills; a crash loses up to N windows of "
            "uncommitted work)"
        ),
    )

    args = parser.parse_args()
    config = get_config(args.adapter_type)

    id_mode = bool(args.ids or args.ids_file)
    if id_mode and args.event:
        raise ValueError("--event is window mode; do not combine it with --ids")
    if not id_mode and not args.event:
        raise ValueError("Supply --event for window mode, or --ids/--ids-file")

    event: LoaderEvent
    if id_mode:
        id_kwargs: dict[str, Any] = {
            "mode": "ids",
            "adapter_type": args.adapter_type,
            "job_id": args.job_id or f"idload-{create_job_id()}",
            "ids": _read_ids(args),
        }
        if args.commit_every is not None:
            id_kwargs["commit_every"] = args.commit_every
        event = OAIPMHIdLoaderEvent.model_validate(id_kwargs)
        step_config = LoaderStepConfig(
            use_rest_api_table=args.use_rest_api_table,
            suppress_summaries=False,
        )
    else:
        with open(args.event, encoding="utf-8") as f:
            event_data = json.load(f)
            # Set allow_partial_final_window from CLI arg if not in event
            if "allow_partial_final_window" not in event_data:
                event_data["allow_partial_final_window"] = (
                    args.allow_partial_final_window
                )
            event = OAIPMHLoaderEvent.model_validate(event_data)

        step_config = LoaderStepConfig(
            use_rest_api_table=args.use_rest_api_table,
            window_minutes=event.window_minutes,
            allow_partial_final_window=(
                event.allow_partial_final_window
                if event.allow_partial_final_window is not None
                else args.allow_partial_final_window
            ),
            suppress_summaries=False,
            reprocess_successful_windows=args.reprocess_successful_windows,
            flush_every=args.flush_every,
        )

    runtime = build_runtime(config, step_config)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step=f"{config.config.pipeline_step_prefix}_loader",
    )
    response = handler(event, runtime, execution_context=execution_context)

    print(json.dumps(response.model_dump(mode="json")))


def ecs_task_handler(
    event: LoaderEvent,
    execution_context: ExecutionContext,
) -> LoaderResponse:
    """ECS task handler invoked by ecs_handler utility."""
    config = get_config(event.adapter_type)
    execution_context = ExecutionContext(
        trace_id=execution_context.trace_id,
        pipeline_step=f"{config.config.pipeline_step_prefix}_loader",
    )
    runtime = build_runtime(config)
    return handler(event, runtime, execution_context=execution_context)


def event_validator(raw_input: str) -> LoaderEvent:
    """Validate raw JSON input into whichever loader event the mode selects."""
    return validate_loader_event(json.loads(raw_input))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run OAI-PMH adapter loader")
    parser.add_argument(
        "--use-cli",
        action="store_true",
        help="Invoke the local CLI handler instead of the ECS handler.",
    )
    args, _ = parser.parse_known_args()

    if args.use_cli:
        local_handler(parser)
    else:
        ecs_handler(
            arg_parser=parser,
            handler=ecs_task_handler,
            event_validator=event_validator,
            pipeline_step="oai_pmh_adapter_loader",
        )
