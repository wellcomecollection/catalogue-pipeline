"""Core sync loop and OKAPI configuration for the Axiell → FOLIO sync step.

Extracted from ``axiell_folio_sync.py`` for readability. Contains:
- ``load_okapi_config`` — resolves FOLIO credentials from env / SSM
- ``run_sync`` — the select → map → upsert loop over adapter rows
"""

from __future__ import annotations

import json
import os
from functools import lru_cache
from typing import Any, cast

import boto3
import structlog

from adapters.steps.axiell_folio_sync.models import (
    AxiellFolioSyncEvent,
    AxiellFolioSyncResponse,
)
from adapters.steps.axiell_folio_sync.report import AxiellFolioSyncReport
from adapters.steps.axiell_folio_sync.s3_manifest import (
    utc_now_iso,
    flush_success_batch,
    write_error_manifest,
    write_metadata_manifest,
)
from adapters.transformers.axiell_folio_sync.folio_callables import (
    FolioInventoryOps,
)
from adapters.transformers.axiell_folio_sync.mapping import (
    MappingError,
    UpsertResult,
    select_and_build,
)
from adapters.transformers.axiell_folio_sync.ref_cache import RefCache
from adapters.transformers.axiell_folio_sync.upsert import upsert_from_payloads

logger = structlog.get_logger(__name__)

# In-memory success buffer is flushed to S3 in chunks of this size.
BATCH_SIZE = 5000


# ── lazy singletons (survive across warm Lambda invocations) ──────────────────


@lru_cache(maxsize=1)
def _s3() -> Any:
    return boto3.client("s3", region_name=os.environ["AWS_REGION"])


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
    job_id: str, source_id: str, changeset_id: str, stage: str, error: str | list
) -> dict:
    """Construct a per-row error entry for the error manifest."""
    key = "errors" if isinstance(error, list) else "error"
    return {
        "jobId": job_id,
        "sourceId": source_id,
        "changesetId": changeset_id,
        "stage": stage,
        key: error,
        "timestamp": utc_now_iso(),
    }


def _tally_upsert_actions(result: UpsertResult, counts: dict[str, int]) -> None:
    """Increment counts for each entity action in a successful upsert."""
    for entity in ("instance", "holdings", "item"):
        action = getattr(result, entity).action
        if action in ("create", "update", "suppress"):
            counts[action + "d"] += 1


# ── core ──────────────────────────────────────────────────────────────────────


def run_sync(
    event: AxiellFolioSyncEvent,
    rows: list[dict],
    ref_cache: RefCache,
    folio: FolioInventoryOps,
    *,
    dry_run: bool,
    manifest_bucket: str | None = None,
) -> AxiellFolioSyncResponse:
    """Select, map, and upsert pre-read adapter rows to FOLIO.

    Dependencies (rows, ref cache, FOLIO client) are injected so the loop is
    unit-testable without SSM / Iceberg / FOLIO; ``handler`` builds the real ones.
    """
    logger.info(
        "axiell_folio_sync start",
        job_id=event.job_id,
        changeset_ids=event.changeset_ids,
        dry_run=dry_run,
    )

    successful_batch: list[dict] = []
    errors_list: list[dict] = []
    total_successful = 0
    total_errors = 0
    counts: dict[str, int] = {
        "created": 0,
        "updated": 0,
        "suppressed": 0,
        "skipped": 0,
        "tombstone": 0,
        "failed": 0,
        "total": 0,
    }

    for row in rows:
        source_id: str = row.get("id", "unknown")
        changeset_id: str = row.get("changeset", "unknown")
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
                _build_error(event.job_id, source_id, changeset_id, "scan", "empty_content")
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
                _build_error(event.job_id, source_id, changeset_id, "mapping", str(exc))
            )
            continue
        except Exception as exc:
            counts["failed"] += 1
            total_errors += 1
            logger.warning("selection_error", source_id=source_id, error=str(exc))
            errors_list.append(
                _build_error(event.job_id, source_id, changeset_id, "selection", str(exc))
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
                _build_error(event.job_id, source_id, changeset_id, "upsert",
                             [e.model_dump() for e in result.errors])
            )
        else:
            total_successful += 1
            _tally_upsert_actions(result, counts)

            successful_batch.append(
                {
                    "jobId": event.job_id,
                    "sourceId": source_id,
                    "changesetId": changeset_id,
                    "instanceAction": result.instance.action,
                    "holdingsAction": result.holdings.action,
                    "itemAction": result.item.action,
                    "timestamp": utc_now_iso(),
                }
            )

            if len(successful_batch) >= BATCH_SIZE and manifest_bucket:
                flush_success_batch(
                    _s3(), manifest_bucket, event.job_id, successful_batch
                )
                successful_batch = []

        logger.info(
            "upsert_result",
            source_id=source_id,
            instance=result.instance.action or "skip",
            holdings=result.holdings.action or "skip",
            item=result.item.action or "skip",
            errors=len(result.errors),
        )

    if successful_batch and manifest_bucket:
        flush_success_batch(_s3(), manifest_bucket, event.job_id, successful_batch)

    manifest_path: str | None = None
    if manifest_bucket:
        if errors_list:
            write_error_manifest(_s3(), manifest_bucket, event.job_id, errors_list)
        manifest_path = write_metadata_manifest(
            _s3(),
            manifest_bucket,
            event.job_id,
            total_successful,
            total_errors,
            has_errors_file=bool(errors_list),
            changeset_ids=event.changeset_ids or None,
        )

    AxiellFolioSyncReport(dry_run=dry_run, counts=counts).publish()

    logger.info(
        "axiell_folio_sync complete",
        job_id=event.job_id,
        total=counts["total"],
        successful=total_successful,
        errors=total_errors,
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
    )
