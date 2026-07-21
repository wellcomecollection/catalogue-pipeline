"""Adapter-side reconcile step for Axiell guid changes.

Runs in the adapter state machine between the loader and the publish event.
Reads the just-loaded changeset rows, computes id->GUID mappings, diffs them
against the reconciler store, and records each superseded guid as a durable
deletion fact before committing the new mappings. Facts are written first
because the mappings commit destroys the diff: a guid change never reappears
in a later changeset, so publishing without facts would lose the deletion
permanently. Re-runs are idempotent — facts deduplicate on their deterministic
ids and the mappings commit is guid-compared and timestamp-gated.
"""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from typing import Any

import pyarrow as pa
import structlog
from pydantic import BaseModel, ConfigDict, Field
from pyiceberg.expressions import In

from adapters.extractors.oai_pmh.registry import get_config
from adapters.steps.oai_pmh.guid_mapping import rows_to_mappings
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


class ReconcileEvent(BaseModel):
    """Input payload: the loader response merged with the adapter type by the
    state machine. Extra keys (summaries, changed_record_count, ...) are
    ignored."""

    job_id: str
    adapter_type: str
    changeset_ids: list[str]
    covered_window_keys: list[str] = Field(default_factory=list)


class ReconcileResponse(BaseModel):
    job_id: str
    adapter_type: str
    changeset_ids: list[str]
    covered_window_keys: list[str]
    facts_written: int
    mappings_inserted: int
    mappings_updated: int
    skipped: int
    guard_suppressed: int


class ReconcileRuntime(BaseModel):
    adapter_store: AdapterStore
    reconciler_store: ReconcilerStore
    facts_store: DeletionFactsStore
    adapter_name: str
    namespace: str

    model_config = ConfigDict(arbitrary_types_allowed=True)


def handler(
    event: ReconcileEvent,
    runtime: ReconcileRuntime,
    execution_context: ExecutionContext | None = None,
) -> ReconcileResponse:
    """Detect guid changes in the event's changesets and record them as facts.

    Soft-deleted rows come back from the changeset read too; their content is
    preserved, so the guid recomputes identically and produces no diff.
    """
    setup_logging(execution_context)

    rows = runtime.adapter_store.get_records_by_changesets(
        event.changeset_ids
    ).to_pylist()
    changeset_by_record_id = {row["id"]: row["changeset"] for row in rows}

    mappings, skipped_ids = rows_to_mappings(rows, runtime.namespace)
    if skipped_ids:
        logger.warning(
            "Skipped rows with no derivable GUID",
            adapter=runtime.adapter_name,
            job_id=event.job_id,
            skipped=len(skipped_ids),
            row_ids=skipped_ids,
        )

    # Superseded mappings: existing rows whose guid the incoming mappings change
    record_ids_to_overwrite = runtime.reconciler_store.get_ids_to_update(mappings)
    candidates = runtime.reconciler_store.get_namespace_records(
        In("id", record_ids_to_overwrite)
    ).to_pylist()

    # Use last_modified from the incoming rows (not the superseded mappings);
    # collect each guid's claimants for the handoff guard along the way.
    last_modified_by_id: dict[str, Any] = {}
    claimants_by_guid: dict[str, set[str]] = defaultdict(set)
    for mapping in mappings.to_pylist():
        last_modified_by_id[mapping["id"]] = mapping["last_modified"]
        claimants_by_guid[mapping["guid"]].add(mapping["id"])
    if candidates:
        # Stored rows that this commit remaps to a different guid (ids in
        # record_ids_to_overwrite) no longer claim their old guid post-commit,
        # so they must not count as claimants: otherwise two records leaving a
        # shared guid in one run would each suppress the other's fact and the
        # deletion would be lost permanently. Their forward-state claims are
        # already covered by the incoming-mappings pass above. Rows whose
        # incoming update was timestamp-gated out are not in the overwrite set
        # and correctly still count.
        overwritten_ids = set(record_ids_to_overwrite)
        guid_filter = In("guid", [row["guid"] for row in candidates])
        for stored in runtime.reconciler_store.get_namespace_records(
            guid_filter
        ).to_pylist():
            if stored["id"] not in overwritten_ids:
                claimants_by_guid[stored["guid"]].add(stored["id"])

    facts: list[dict[str, Any]] = []
    guard_suppressed = 0
    for row in candidates:
        # A guid claimed by a different record (in the incoming mappings or
        # the store) is a handoff, not a deletion: the work stays live under
        # its new owner, so tombstoning it would delete a live work. A
        # candidate never claims its own old guid (its incoming mapping holds
        # the new guid, and its stored row is excluded above).
        other_claimants = claimants_by_guid[row["guid"]]
        if other_claimants:
            guard_suppressed += 1
            logger.warning(
                "Suppressed deletion fact for handed-off guid",
                adapter=runtime.adapter_name,
                job_id=event.job_id,
                record_id=row["id"],
                guid=row["guid"],
                claimed_by=sorted(other_claimants),
            )
            continue

        changeset_id = changeset_by_record_id[row["id"]]
        facts.append(
            {
                "namespace": runtime.namespace,
                "id": f"{row['id']}/{changeset_id}",
                "record_id": row["id"],
                "guid": row["guid"],
                "changeset": changeset_id,
                "last_modified": last_modified_by_id.get(
                    row["id"], row["last_modified"]
                ),
            }
        )

    # Facts must be durable before the mappings commit destroys the diff; a
    # failure here leaves the mappings untouched, so a re-run recomputes the
    # same facts and append_facts deduplicates any that did land.
    facts_written = runtime.facts_store.append_facts(
        pa.Table.from_pylist(facts, schema=runtime.facts_store.schema)
    )

    commit_result = runtime.reconciler_store.incremental_update(mappings)
    mappings_inserted = len(commit_result.inserted_record_ids) if commit_result else 0
    mappings_updated = len(commit_result.updated_record_ids) if commit_result else 0

    logger.info(
        "Reconcile step complete",
        adapter=runtime.adapter_name,
        job_id=event.job_id,
        changesets=len(event.changeset_ids),
        rows=len(rows),
        facts_written=facts_written,
        mappings_inserted=mappings_inserted,
        mappings_updated=mappings_updated,
        skipped=len(skipped_ids),
        guard_suppressed=guard_suppressed,
    )
    return ReconcileResponse(
        job_id=event.job_id,
        adapter_type=event.adapter_type,
        changeset_ids=event.changeset_ids,
        covered_window_keys=event.covered_window_keys,
        facts_written=facts_written,
        mappings_inserted=mappings_inserted,
        mappings_updated=mappings_updated,
        skipped=len(skipped_ids),
        guard_suppressed=guard_suppressed,
    )


def build_runtime(
    adapter_type: str, use_rest_api_table: bool = True
) -> ReconcileRuntime:
    config = get_config(adapter_type)
    if not hasattr(config, "build_reconciler_table") or not hasattr(
        config, "build_deletion_facts_table"
    ):
        raise ValueError(
            f"Adapter '{adapter_type}' has no reconciler table; "
            "reconcile is Axiell-only"
        )
    reconciler_table = config.build_reconciler_table(
        use_rest_api_table=use_rest_api_table
    )
    facts_table = config.build_deletion_facts_table(
        use_rest_api_table=use_rest_api_table
    )
    return ReconcileRuntime(
        adapter_store=config.build_adapter_store(use_rest_api_table=use_rest_api_table),
        reconciler_store=ReconcilerStore(
            reconciler_table, namespace=config.config.adapter_namespace
        ),
        facts_store=DeletionFactsStore(
            facts_table, namespace=config.config.adapter_namespace
        ),
        adapter_name=config.config.adapter_name,
        namespace=config.config.adapter_namespace,
    )


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Unified Lambda entry point for the OAI-PMH reconcile step.

    Resolves the adapter config from the ``adapter_type`` field, injected by
    the state machine alongside the loader response.
    """
    adapter_type = event.get("adapter_type")
    if adapter_type is None:
        raise ValueError("Event must contain 'adapter_type'")

    config = get_config(adapter_type)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step=f"{config.config.pipeline_step_prefix}_reconcile",
    )
    response = handler(
        ReconcileEvent.model_validate(event),
        runtime=build_runtime(adapter_type),
        execution_context=execution_context,
    )
    return response.model_dump(mode="json")


def local_handler(parser: argparse.ArgumentParser) -> None:
    """Run the reconcile step from the command line."""
    from adapters.utils.argparse import add_adapter_event_args

    add_adapter_event_args(parser)
    parser.add_argument(
        "--changeset-ids",
        type=str,
        required=True,
        help="Comma-separated adapter changeset ids to reconcile",
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
        pipeline_step=f"{config.config.pipeline_step_prefix}_reconcile",
    )
    response = handler(
        ReconcileEvent(
            job_id=args.job_id,
            adapter_type=args.adapter_type,
            changeset_ids=[
                changeset_id.strip()
                for changeset_id in args.changeset_ids.split(",")
                if changeset_id.strip()
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
        argparse.ArgumentParser(description="Run the OAI-PMH reconcile step locally")
    )
