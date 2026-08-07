"""
Adapter-store read for the Axiell → FOLIO sync step.

Reads changed Axiell adapter rows and superseded-GUID deletions via the shared
``AxiellChangesetReader``, keeping the entrypoint free of Iceberg / adapter-store
concerns. Read-only: the tables are never created.
"""

from __future__ import annotations

import structlog

from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG
from adapters.utils.axiell_changeset_reader import (
    AxiellChangesetReader,
    SupersededGuid,
)

logger = structlog.get_logger(__name__)


def read_rows(
    changeset_ids: list[str] | None,
    sample_limit: int | None,
    *,
    use_rest_api_table: bool,
) -> tuple[list[dict], list[SupersededGuid]]:
    """Read changed Axiell adapter rows and superseded-GUID deletions.

    Uses the same ``AXIELL_CONFIG`` as the Axiell adapter, so it works against
    S3 Tables (``use_rest_api_table=True``, production) or the local sqlite
    catalog (``use_rest_api_table=False``, local dev) with no code changes.
    Read-only: the tables are never created.

    Selection precedence: ``changeset_ids`` (the production path) first, then a
    sample of active records.

    Deletion facts are read only for incremental (changeset) runs. The reconcile
    step in the adapter state machine writes them into the same table bucket
    before ``axiell.adapter.completed`` fires, and ``iter_deletions`` re-checks
    each fact against the current reconciler mappings, so a GUID reclaimed by a
    revert/handoff never suppresses a live record. A sample run (no changesets)
    has nothing to overwrite, so it reads no facts and the reader is built
    without the facts/reconciler tables.
    """
    reader = AxiellChangesetReader.build(
        AXIELL_CONFIG,
        changeset_ids or [],
        use_rest_api_table=use_rest_api_table,
        with_deletion_facts=bool(changeset_ids),
    )

    if changeset_ids:
        logger.info("adapter_read", mode="changesets", changeset_ids=changeset_ids)
        records = list(reader.iter_records())
        deletions = list(reader.iter_deletions())
        return records, deletions

    # Dev/smoke-test fallback: a sample of active records (no changesets given).
    limit = sample_limit or 10
    logger.info("adapter_read", mode="sample", limit=limit)
    rows: list[dict] = []
    for row in reader.iter_records():
        rows.append(row)
        if len(rows) >= limit:
            break
    return rows, []
