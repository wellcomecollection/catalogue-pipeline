"""Work-discovery step for the adapter transformer state machine.

Plans an adapter event into bounded transformer partitions, so no single
transformer invocation does unbounded work. Partitions are groups of whole
changesets, sized by row count, all read at one pinned snapshot: the transformer
stays in changeset mode, so tombstones and Axiell deletion facts are delivered
exactly as a single changeset run delivers them. A reindex (`full: true`)
plans every changeset in the store.
"""

from __future__ import annotations

import typing

import structlog
from pydantic import BaseModel, ConfigDict, Field, model_validator

from adapters.steps.transformer import (
    CONFIG_BY_TYPE,
    TransformerEvent,
    TransformerType,
    get_adapter_store,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg_changeset_source import IcebergChangesetSource
from core.find_work import group_by_weight, write_partitions_to_s3
from models.find_work import FindWorkRefsResult
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

DEFAULT_PARTITION_ROWS = 10_000
"""About 35 s of FOLIO transforming, well inside the 600 s Lambda timeout."""

MAX_TOMBSTONES_PER_EVENT = 5_000
"""The most tombstones any changeset in the stores has carried is 129, so an
event with thousands more likely comes from a faulty harvest than real deletions."""


class TransformerFindWorkEvent(BaseModel):
    """An adapter or reindex event as the planner reads it.

    Unknown fields (for example FOLIO's items_changeset_ids) are ignored, but an
    event must name its changesets or ask for `full: true`, so a malformed one
    fails instead of planning nothing.
    """

    model_config = ConfigDict(extra="ignore")

    transformer_type: TransformerType
    job_id: str
    changeset_ids: list[str] = Field(default_factory=list)
    full: bool = False
    snapshot_id: int | None = None
    partition_rows: int = Field(default=DEFAULT_PARTITION_ROWS, gt=0, le=50_000)
    max_tombstones: int = Field(default=MAX_TOMBSTONES_PER_EVENT, ge=0)

    @model_validator(mode="after")
    def _check_scope(self) -> TransformerFindWorkEvent:
        if bool(self.changeset_ids) == self.full:
            raise ValueError("Give changeset_ids or full: true, not both or neither")
        return self


def parse_event(payload: dict[str, typing.Any]) -> TransformerFindWorkEvent:
    """Accept the EventBridge rules' `{"detail": ...}` wrapper or a bare event."""
    return TransformerFindWorkEvent.model_validate(payload.get("detail", payload))


def plan(
    event: TransformerFindWorkEvent, store: AdapterStore
) -> list[TransformerEvent]:
    source = IcebergChangesetSource(
        store,
        changeset_ids=None if event.full else event.changeset_ids,
        snapshot_id=event.snapshot_id,
    )
    if not event.full and source.tombstone_count > event.max_tombstones:
        raise RuntimeError(
            f"{source.tombstone_count} tombstones in job {event.job_id} exceeds "
            f"the {event.max_tombstones} per-event limit; check the harvest, then "
            "replay with a raised max_tombstones if the deletions are real"
        )

    groups = group_by_weight(source.changesets, event.partition_rows)
    partitions = [
        TransformerEvent(
            transformer_type=event.transformer_type,
            job_id=f"{event.job_id}-p{index:03d}",
            changeset_ids=group,
            snapshot_id=source.snapshot_id,
        )
        for index, group in enumerate(groups)
    ]

    logger.info(
        "Planned transformer partitions",
        job_id=event.job_id,
        transformer_type=event.transformer_type,
        full=event.full,
        changeset_count=len(source.changesets),
        record_count=source.row_count,
        tombstone_count=source.tombstone_count,
        partition_count=len(partitions),
        snapshot_id=source.snapshot_id,
    )
    return partitions


def partition_s3_uri(event: TransformerFindWorkEvent, index: int) -> str:
    """Next to the transformer's reports, keyed by job so a replay overwrites."""
    config = CONFIG_BY_TYPE[event.transformer_type]
    return (
        f"s3://{config.S3_BUCKET}/pipeline-{config.PIPELINE_DATE}/"
        f"{event.transformer_type}/{config.S3_PREFIX}/find_work/{event.job_id}/"
        f"partition-{index}.json"
    )


def lambda_handler(
    event: dict[str, typing.Any], context: typing.Any
) -> dict[str, typing.Any]:
    setup_logging(
        ExecutionContext(
            trace_id=get_trace_id(context),
            pipeline_step="adapter_transformer_find_work",
        )
    )
    parsed = parse_event(event)
    store = get_adapter_store(parsed.transformer_type, use_rest_api_table=True)
    partitions = plan(parsed, store)

    refs = write_partitions_to_s3(
        partitions,
        [len(p.changeset_ids) for p in partitions],
        lambda index: partition_s3_uri(parsed, index),
    )
    return FindWorkRefsResult(partitions=refs).model_dump(mode="json")
