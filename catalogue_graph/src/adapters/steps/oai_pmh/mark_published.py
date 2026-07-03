"""Generic mark-published step for OAI-PMH adapters.

Runs at the end of the adapter state machine, after the publish event (or the
quiet no-publish path), and stamps the covered windows' rows with a
``published_at`` tag. The trigger resumes from the last published window, so
any window that was loaded but whose execution died before publishing stays
inside the next harvest range and its changesets are re-emitted and
re-published automatically.
"""

from __future__ import annotations

import argparse
import json
from datetime import UTC, datetime
from typing import Any

import structlog
from pydantic import BaseModel, ConfigDict

from adapters.extractors.oai_pmh.registry import get_config
from adapters.utils.window_store import WindowStore
from adapters.utils.window_summary import published_at_from_tags
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


class MarkPublishedEvent(BaseModel):
    """Input payload: the loader/enrichment response merged with the adapter
    type by the state machine. Extra keys (changeset_ids,
    changed_record_count, ...) are ignored."""

    job_id: str
    adapter_type: str
    covered_window_keys: list[str]


class MarkPublishedResponse(BaseModel):
    job_id: str
    adapter_type: str
    windows_stamped: int
    windows_skipped: int
    last_published_end: datetime | None


class MarkPublishedRuntime(BaseModel):
    store: WindowStore
    adapter_name: str

    model_config = ConfigDict(arbitrary_types_allowed=True)


def handler(
    event: MarkPublishedEvent,
    runtime: MarkPublishedRuntime,
    execution_context: ExecutionContext | None = None,
    now: datetime | None = None,
) -> MarkPublishedResponse:
    """Stamp the covered windows with a published_at tag.

    Stamps exactly the rows named by the loader response's
    ``covered_window_keys`` — the success windows whose changesets this
    execution actually carried to the publish event. Keying on the response
    (rather than a time range) means rows written by a concurrently running
    execution, or rows whose changesets the loader never re-emitted, are
    never marked published here.

    Only rows still in ``state == "success"`` are stamped, and already-stamped
    rows are skipped, preserving their original publish timestamp and keeping
    retries idempotent.
    """
    setup_logging(execution_context)
    now = now or datetime.now(UTC)

    rows = runtime.store.list_by_keys(event.covered_window_keys)

    to_stamp = []
    skipped = 0
    for row in rows:
        if row.state != "success":
            continue
        if published_at_from_tags(row.tags) is not None:
            skipped += 1
            continue
        tags = {**(row.tags or {}), "published_at": now.isoformat()}
        to_stamp.append(row.model_copy(update={"tags": tags, "updated_at": now}))

    runtime.store.upsert_many(to_stamp)

    stamped_ends = [row.window_end for row in to_stamp]
    last_published_end = max(stamped_ends) if stamped_ends else None
    logger.info(
        "Marked windows as published",
        adapter=runtime.adapter_name,
        job_id=event.job_id,
        covered_window_keys=len(event.covered_window_keys),
        windows_stamped=len(to_stamp),
        windows_skipped=skipped,
        last_published_end=last_published_end.isoformat()
        if last_published_end
        else None,
    )
    return MarkPublishedResponse(
        job_id=event.job_id,
        adapter_type=event.adapter_type,
        windows_stamped=len(to_stamp),
        windows_skipped=skipped,
        last_published_end=last_published_end,
    )


def build_runtime(
    adapter_type: str, use_rest_api_table: bool = True
) -> MarkPublishedRuntime:
    config = get_config(adapter_type)
    return MarkPublishedRuntime(
        store=config.build_window_store(use_rest_api_table=use_rest_api_table),
        adapter_name=config.config.adapter_name,
    )


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Unified Lambda entry point for OAI-PMH mark-published steps.

    Resolves the adapter config from the ``adapter_type`` field, injected by
    the state machine alongside the loader/enrichment response.
    """
    adapter_type = event.get("adapter_type")
    if adapter_type is None:
        raise ValueError("Event must contain 'adapter_type'")

    config = get_config(adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=f"{config.config.pipeline_step_prefix}_mark_published",
    )
    response = handler(
        MarkPublishedEvent.model_validate(event),
        runtime=build_runtime(adapter_type),
        execution_context=execution_context,
    )
    return response.model_dump(mode="json")


def local_handler(parser: argparse.ArgumentParser) -> None:
    """Run the mark-published step from the command line."""
    from adapters.utils.argparse import add_adapter_event_args

    add_adapter_event_args(parser)
    parser.add_argument(
        "--window-keys",
        type=str,
        required=True,
        help="Comma-separated window keys to stamp (as stored in window_key)",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        default="local",
        help="Job identifier to log against",
    )

    args = parser.parse_args()
    config = get_config(args.adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step=f"{config.config.pipeline_step_prefix}_mark_published",
    )
    response = handler(
        MarkPublishedEvent(
            job_id=args.job_id,
            adapter_type=args.adapter_type,
            covered_window_keys=[
                key.strip() for key in args.window_keys.split(",") if key.strip()
            ],
        ),
        runtime=build_runtime(
            args.adapter_type, use_rest_api_table=args.use_rest_api_table
        ),
        execution_context=execution_context,
    )
    print(json.dumps(response.model_dump(mode="json")))


if __name__ == "__main__":
    local_handler(
        argparse.ArgumentParser(
            description="Run an OAI-PMH mark-published step locally"
        )
    )
