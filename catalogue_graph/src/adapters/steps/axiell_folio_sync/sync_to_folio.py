"""Core sync loop and OKAPI configuration for the Axiell to Folio sync step.

Extracted from ``axiell_folio_sync.py`` for readability. Contains:
- ``load_okapi_config`` — resolves FOLIO credentials from env / SSM
- ``run_sync`` — the select → map → upsert loop over adapter rows

RFC 090 references below point at the design spec:
https://github.com/wellcomecollection/docs/tree/main/rfcs/090-axiell-folio-sync
"""

from __future__ import annotations

import json
import os
from datetime import UTC, datetime
from functools import lru_cache
from typing import Any, cast

import boto3
import structlog

from adapters.steps.axiell_folio_sync.folio_callables import (
    FolioInventoryOps,
)
from adapters.steps.axiell_folio_sync.mapping import (
    MappingError,
    select_and_build,
)
from adapters.steps.axiell_folio_sync.mapping import (
    GuidCascadeResult,
    UpsertResult,
)
from adapters.steps.axiell_folio_sync.models import (
    AxiellFolioSyncEvent,
    AxiellFolioSyncResponse,
    SyncDeletionEntry,
    SyncErrorEntry,
    SyncSuccessEntry,
)
from adapters.steps.axiell_folio_sync.ref_cache import RefCache
from adapters.steps.axiell_folio_sync.report import AxiellFolioSyncReport
from adapters.steps.axiell_folio_sync.upsert import (
    delete_by_guid,
    suppress_by_guid,
    upsert_from_payloads,
)
from adapters.utils.axiell_changeset_reader import SupersededGuid

logger = structlog.get_logger(__name__)


def utc_now_iso() -> str:
    """Timezone-aware UTC timestamp in ISO 8601."""
    return datetime.now(UTC).isoformat()


# ── lazy singletons (survive across warm Lambda invocations) ──────────────────


@lru_cache(maxsize=1)
def _ssm() -> Any:
    return boto3.client("ssm", region_name=os.environ["AWS_REGION"])


# ── OKAPI config ──────────────────────────────────────────────────────────────


def load_okapi_config() -> dict[str, str]:
    """FOLIO OKAPI url/tenant/username/password from env and/or SSM.

    Per-field env overrides (OKAPI_URL / OKAPI_TENANT / OKAPI_USERNAME /
    OKAPI_PASSWORD) let local runs skip SSM entirely; in Lambda these usually
    come from the OKAPI_SECRET_PARAM SecureString JSON.
    """
    data: dict[str, str] = {}
    param_name = os.environ.get("OKAPI_SECRET_PARAM")
    if param_name:
        param = _ssm().get_parameter(Name=param_name, WithDecryption=True)
        data = json.loads(param["Parameter"]["Value"])

    merged = {
        "url": os.environ.get("OKAPI_URL") or data.get("url"),
        "tenant": os.environ.get("OKAPI_TENANT") or data.get("tenant"),
        "username": os.environ.get("OKAPI_USERNAME") or data.get("username"),
        "password": os.environ.get("OKAPI_PASSWORD") or data.get("password"),
    }
    missing = [key for key, value in merged.items() if not value]
    if missing:
        missing_list = ", ".join(missing)
        raise ValueError(
            "Missing OKAPI configuration fields: "
            f"{missing_list}. Provide OKAPI_* env vars or set OKAPI_SECRET_PARAM."
        )

    return cast("dict[str, str]", merged)


# ── sync loop helpers ─────────────────────────────────────────────────────────


def _build_error(
    source_id: str, changeset_id: str, stage: str, error: str | list[dict[str, Any]]
) -> SyncErrorEntry:
    """Construct a per-row error entry for the run report."""
    return SyncErrorEntry(
        source_id=source_id,
        changeset_id=changeset_id,
        stage=stage,
        error=error,
        timestamp=utc_now_iso(),
    )


# Maps an entity action to its plural count key. "suppress" -> "suppressed"
# (not "suppressd"), so this cannot be derived by appending "d".
_ACTION_COUNT_KEY = {
    "create": "created",
    "update": "updated",
    "suppress": "suppressed",
    "delete": "deleted",
}


def _tally_entity_actions(result: UpsertResult | GuidCascadeResult, counts: dict[str, int]) -> None:
    """Increment counts for each entity action in a successful upsert/suppress."""
    for entity in ("instance", "holdings", "item"):
        key = _ACTION_COUNT_KEY.get(getattr(result, entity).action)
        if key:
            counts[key] += 1


def _run_reconcile_deletions(
    deletions: list[SupersededGuid],
    folio: FolioInventoryOps,
    counts: dict[str, int],
    errors_list: list[SyncErrorEntry],
    *,
    dry_run: bool,
    hard_delete: bool,
) -> tuple[list[SyncDeletionEntry], int]:
    """Action each superseded GUID's FOLIO records (item → holdings → instance).

    ``hard_delete`` selects irreversible DELETE; otherwise the records are
    soft-suppressed (the reversible default). A per-GUID FOLIO failure is recorded
    as an error entry and does not abort the run: the upsert pass has already
    committed, and the reconciler mapping/ES delete are upstream and independent.
    Returns the manifest entries for every GUID that actioned at least one entity
    (including partial cascades that then errored), and the count of GUIDs that
    errored.
    """
    action_by_guid = delete_by_guid if hard_delete else suppress_by_guid
    stage = "delete" if hard_delete else "suppress"
    entries: list[SyncDeletionEntry] = []
    error_count = 0

    for superseded in deletions:
        counts["deletions"] += 1
        result = action_by_guid(superseded.guid, folio, dry_run=dry_run)

        # Tally and record whatever was actually actioned, even when the cascade
        # later failed: a partial hard-delete may have already removed a child
        # (irreversibly) before erroring on its parent, and that must not vanish
        # from the counts or the manifest. Entities that never ran carry a null
        # action, so nothing is over-counted.
        _tally_entity_actions(result, counts)
        if any(
            getattr(result, entity).action
            for entity in ("instance", "holdings", "item")
        ):
            entries.append(
                SyncDeletionEntry(
                    guid=superseded.guid,
                    record_id=superseded.record_id,
                    changeset_id=superseded.changeset_id,
                    instance_action=result.instance.action,
                    holdings_action=result.holdings.action,
                    item_action=result.item.action,
                    timestamp=utc_now_iso(),
                )
            )

        if result.errors:
            error_count += 1
            errors_list.append(
                _build_error(
                    superseded.guid,
                    superseded.changeset_id,
                    stage,
                    [e.model_dump() for e in result.errors],
                )
            )

        logger.info(
            "reconcile_delete_result",
            mode="delete" if hard_delete else "suppress",
            guid=superseded.guid,
            instance=result.instance.action or "skip",
            holdings=result.holdings.action or "skip",
            item=result.item.action or "skip",
            errors=len(result.errors),
        )

    return entries, error_count


# ── core ──────────────────────────────────────────────────────────────────────


def run_sync(
    event: AxiellFolioSyncEvent,
    rows: list[dict],
    ref_cache: RefCache,
    folio: FolioInventoryOps,
    *,
    dry_run: bool,
    manifest_bucket: str | None = None,
    deletions: list[SupersededGuid] | None = None,
    hard_delete: bool = False,
) -> AxiellFolioSyncResponse:
    """Select, map, and upsert pre-read adapter rows to FOLIO.

    Dependencies (rows, ref cache, FOLIO client) are injected so the loop is
    unit-testable without SSM / Iceberg / FOLIO; ``handler`` builds the real ones.

    ``deletions`` carries the reconciler's superseded GUIDs for this changeset
    batch. They are actioned in a second pass *after* the upsert pass so a record
    re-created and superseded in the same batch is handled in write order; the
    reader has already dropped any GUID reclaimed by a current mapping, so a live
    record is never touched here. ``hard_delete`` selects irreversible DELETE for
    that pass; the default is reversible soft-suppression.
    """
    logger.info(
        "axiell_folio_sync start",
        job_id=event.job_id,
        changeset_ids=event.changeset_ids,
        dry_run=dry_run,
    )

    successful: list[SyncSuccessEntry] = []
    errors_list: list[SyncErrorEntry] = []
    total_successful = 0
    total_errors = 0
    counts: dict[str, int] = {
        "created": 0,
        "updated": 0,
        "suppressed": 0,
        "deleted": 0,
        "skipped": 0,
        "tombstone": 0,
        "failed": 0,
        "total": 0,
        # Superseded GUIDs (reconciler deletions) processed in the second pass.
        "deletions": 0,
    }

    for row in rows:
        source_id = cast(str, row["id"])
        changeset_id = cast(str, row["changeset"])
        counts["total"] += 1

        # Loader tombstones are advisory only (RFC 090): the loader's deleted=true is
        # unreliable, so we record and metric the signal but do NOT suppress/remove.
        # Authoritative deletes come from the reconciler, not this path.
        if row.get("deleted"):
            counts["tombstone"] += 1
            logger.info(
                "tombstone_advisory", source_id=source_id, changeset_id=changeset_id
            )
            continue

        if not row.get("content"):
            counts["failed"] += 1
            total_errors += 1
            logger.warning("empty_content", source_id=source_id)
            errors_list.append(
                _build_error(source_id, changeset_id, "scan", "empty_content")
            )
            continue

        # Record selection + mapping in a single XML parse (RFC 090).
        # Returns None if not selected, raises MappingError on mapping failures,
        # and raises other exceptions on malformed XML.
        try:
            mapped = select_and_build(row["content"], ref_cache)
        except MappingError as exc:
            counts["failed"] += 1
            total_errors += 1
            logger.warning("mapping_error", source_id=source_id, error=str(exc))
            errors_list.append(
                _build_error(source_id, changeset_id, "mapping", str(exc))
            )
            continue
        except Exception as exc:
            counts["failed"] += 1
            total_errors += 1
            logger.warning("selection_error", source_id=source_id, error=str(exc))
            errors_list.append(
                _build_error(source_id, changeset_id, "selection", str(exc))
            )
            continue

        if mapped is None:
            counts["skipped"] += 1
            logger.info(
                "skipped_not_selected", source_id=source_id, changeset_id=changeset_id
            )
            continue

        result = upsert_from_payloads(
            mapped,
            folio=folio,
            dry_run=dry_run,
            ref_cache=ref_cache,
        )

        if result.errors:
            counts["failed"] += 1
            total_errors += 1
            errors_list.append(
                _build_error(
                    source_id,
                    changeset_id,
                    "upsert",
                    [e.model_dump() for e in result.errors],
                )
            )
        else:
            total_successful += 1
            _tally_entity_actions(result, counts)

            successful.append(
                SyncSuccessEntry(
                    source_id=source_id,
                    changeset_id=changeset_id,
                    instance_action=result.instance.action,
                    holdings_action=result.holdings.action,
                    item_action=result.item.action,
                    timestamp=utc_now_iso(),
                )
            )

        logger.info(
            "upsert_result",
            source_id=source_id,
            instance=result.instance.action or "skip",
            holdings=result.holdings.action or "skip",
            item=result.item.action or "skip",
            errors=len(result.errors),
        )

    # ── Pass 2: authoritative deletes (reconciler superseded GUIDs) ────────────
    deletion_entries, deletion_errors = _run_reconcile_deletions(
        deletions or [],
        folio,
        counts,
        errors_list,
        dry_run=dry_run,
        hard_delete=hard_delete,
    )
    # Fold both passes into the totals symmetrically: previously only deletion
    # *errors* counted, so total_records (= successful + errors) silently dropped
    # cleanly-actioned deletions. Every processed GUID is either an error or a
    # success, so successes = processed − errors.
    deletion_successes = counts["deletions"] - deletion_errors
    total_successful += deletion_successes
    total_errors += deletion_errors

    report = AxiellFolioSyncReport(
        job_id=event.job_id,
        changeset_ids=event.changeset_ids,
        dry_run=dry_run,
        counts=counts,
        successful=successful,
        errors=errors_list,
        deletions=deletion_entries,
        s3_bucket=manifest_bucket,
        # The S3 report is written even on dry runs (it is how dry runs are
        # validated); only CloudWatch metrics are suppressed.
        publish_to_s3=bool(manifest_bucket),
    )
    report.publish()
    manifest_path = report.s3_uri if manifest_bucket else None

    logger.info(
        "axiell_folio_sync complete",
        job_id=event.job_id,
        total=counts["total"],
        successful=total_successful,
        errors=total_errors,
        deletions=counts["deletions"],
        dry_run=dry_run,
    )

    return AxiellFolioSyncResponse(
        job_id=event.job_id,
        dry_run=dry_run,
        manifest_s3_path=manifest_path,
        counts=counts,
        total_successful=total_successful,
        total_errors=total_errors,
        total_records=total_successful + total_errors,
        total_deletions=counts["deletions"],
    )
