"""Plans an adapter event into groups of whole changesets, bounded by row count
except where one changeset alone is bigger, at one pinned snapshot."""

from __future__ import annotations

import hashlib
import typing
from collections.abc import Callable

import structlog
from pydantic import BaseModel, ConfigDict, Field, model_validator

from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG
from adapters.steps.transformer import (
    CONFIG_BY_TYPE,
    TransformerEvent,
    TransformerType,
    get_adapter_store,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.iceberg_changeset_source import IcebergChangesetSource
from core.find_work import group_by_weight, write_partitions_to_s3
from models.find_work import FindWorkRefsResult
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

DEFAULT_PARTITION_ROWS = 10_000
"""About 35 s of FOLIO transforming, well inside the 600 s Lambda timeout."""

MAX_TOMBSTONES_PER_EVENT = 5_000
"""Far above the 129 any changeset has carried, so more means a faulty harvest."""

MAX_DELETION_FACTS_PER_EVENT = 1_000
"""The tombstone limit's equivalent for Axiell's reconcile step."""

MAX_CHANGESETS_PER_PARTITION = 500
"""Bounds the changeset filter when a store has many small changesets."""


class TransformerFindWorkEvent(BaseModel):
    """An adapter or reindex event; one with no scope fails rather than plans nothing."""

    model_config = ConfigDict(extra="ignore")

    transformer_type: TransformerType
    job_id: str
    changeset_ids: list[str] | None = None
    full: bool = False
    snapshot_id: int | None = None
    partition_rows: int = Field(default=DEFAULT_PARTITION_ROWS, gt=0, le=50_000)
    max_tombstones: int = Field(default=MAX_TOMBSTONES_PER_EVENT, ge=0)
    max_deletion_facts: int = Field(default=MAX_DELETION_FACTS_PER_EVENT, ge=0)

    @model_validator(mode="after")
    def _check_scope(self) -> TransformerFindWorkEvent:
        if (self.changeset_ids is not None) == self.full:
            raise ValueError("Give changeset_ids or full: true, not both or neither")
        return self


def parse_event(payload: dict[str, typing.Any]) -> TransformerFindWorkEvent:
    """Accept the EventBridge rules' `{"detail": ...}` wrapper or a bare event."""
    return TransformerFindWorkEvent.model_validate(payload.get("detail", payload))


def plan(
    event: TransformerFindWorkEvent,
    store: AdapterStore,
    count_deletion_facts: Callable[[list[str]], int] | None = None,
) -> tuple[list[TransformerEvent], list[int]]:
    """Partitions and their row counts; raises first if deletions exceed the limits."""
    if event.changeset_ids == []:
        logger.info("No changesets to plan", job_id=event.job_id)
        return [], []

    source = IcebergChangesetSource(
        store, changeset_ids=event.changeset_ids, snapshot_id=event.snapshot_id
    )
    if not event.full:
        _check_deletions(event, source, count_deletion_facts)

    oversized = [c for c, rows in source.changesets if rows > event.partition_rows]
    if oversized:
        logger.warning(
            "Changesets exceed the partition size and run as one partition each",
            job_id=event.job_id,
            partition_rows=event.partition_rows,
            changeset_ids=oversized,
        )

    groups = group_by_weight(
        source.changesets, event.partition_rows, MAX_CHANGESETS_PER_PARTITION
    )
    partitions = [
        TransformerEvent(
            transformer_type=event.transformer_type,
            job_id=f"{event.job_id}-p{index:03d}",
            changeset_ids=group,
            snapshot_id=source.snapshot_id,
            with_deletion_facts=not event.full,
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
    rows = dict(source.changesets)
    return partitions, [sum(rows[c] for c in group) for group in groups]


def _check_deletions(
    event: TransformerFindWorkEvent,
    source: IcebergChangesetSource,
    count_deletion_facts: Callable[[list[str]], int] | None,
) -> None:
    if source.tombstone_count > event.max_tombstones:
        raise RuntimeError(
            f"{source.tombstone_count} tombstones in job {event.job_id} exceeds "
            f"the {event.max_tombstones} per-event limit; check the harvest, then "
            "replay with a raised max_tombstones if the deletions are real"
        )
    if count_deletion_facts is None or not event.changeset_ids:
        return
    fact_count = count_deletion_facts(event.changeset_ids)
    if fact_count > event.max_deletion_facts:
        raise RuntimeError(
            f"{fact_count} deletion facts in job {event.job_id} exceeds the "
            f"{event.max_deletion_facts} per-event limit; check the reconcile "
            "step, then replay with a raised max_deletion_facts if they are real"
        )


def count_axiell_deletion_facts(changeset_ids: list[str]) -> int:
    table = AXIELL_CONFIG.build_deletion_facts_table(
        use_rest_api_table=True, create_if_not_exists=False
    )
    facts = DeletionFactsStore(table, namespace=AXIELL_CONFIG.adapter_namespace)
    return facts.get_records_by_changesets(changeset_ids).num_rows


def partition_s3_uri(
    event: TransformerFindWorkEvent, snapshot_id: int | None, index: int
) -> str:
    """Next to the transformer's reports. Events can share a job_id (one per
    changeset from rebuild_adapter.py), so the path also hashes the scope and
    snapshot: only plans with identical contents share files."""
    config = CONFIG_BY_TYPE[event.transformer_type]
    scope = "full" if event.full else "_".join(event.changeset_ids or [])
    plan_key = hashlib.sha256(f"{scope}@{snapshot_id}".encode()).hexdigest()[:12]
    return (
        f"s3://{config.S3_BUCKET}/pipeline-{config.PIPELINE_DATE}/"
        f"{event.transformer_type}/{config.S3_PREFIX}/find_work/{event.job_id}/"
        f"{plan_key}/partition-{index}.json"
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
    count_facts = (
        count_axiell_deletion_facts if parsed.transformer_type == "axiell" else None
    )
    partitions, row_counts = plan(parsed, store, count_facts)
    if not partitions:
        return FindWorkRefsResult(partitions=[]).model_dump(mode="json")

    refs = write_partitions_to_s3(
        partitions,
        row_counts,
        lambda index: partition_s3_uri(parsed, partitions[index].snapshot_id, index),
    )
    return FindWorkRefsResult(partitions=refs).model_dump(mode="json")
