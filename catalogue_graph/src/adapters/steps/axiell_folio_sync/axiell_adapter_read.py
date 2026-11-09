"""
Adapter-store read for the Axiell → FOLIO sync step.

Reads changed Axiell adapter rows, superseded-GUID deletions and tombstoned
GUIDs via the shared ``AxiellChangesetReader``, keeping the entrypoint free of
Iceberg / adapter-store concerns. Read-only: the tables are never created.
"""

from __future__ import annotations

from collections.abc import Callable, Mapping
from dataclasses import dataclass
from typing import Any

import structlog

from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG
from adapters.steps.oai_pmh.guid_mapping import compute_guid
from adapters.utils.axiell_changeset_reader import (
    AxiellChangesetReader,
    SupersededGuid,
)

logger = structlog.get_logger(__name__)


@dataclass(frozen=True)
class TombstonedGuid:
    """A record hard deleted in Collections: `record_id` arrived as an OAI-PMH
    tombstone in `changeset_id`, and `guid` is the 001 of its preserved
    content."""

    record_id: str
    guid: str
    changeset_id: str


def tombstoned_guids(
    rows: list[dict[str, Any]],
    guids_held_by_other_records: Callable[[Mapping[str, str]], set[str]],
) -> list[TombstonedGuid]:
    """The GUIDs of the tombstones in ``rows`` that are safe to delete.

    The adapter writes a tombstone only when the OAI-PMH server reports the
    record deleted, and keeps the record's last content, so its 001 still
    names the GUID the sync created FOLIO records under. The GUID is derived
    with the reconcile step's own ``compute_guid``, so it compares equal to the
    reconciler mappings.

    A tombstone with no content (first seen already deleted) or no derivable
    GUID cannot have been synced, so it is passed over. A GUID that another
    record still holds is dropped and logged: deleting it would remove that
    live record's FOLIO records.
    """
    candidates: dict[str, tuple[str, str]] = {}
    for row in rows:
        if not row.get("deleted"):
            continue
        guid = compute_guid(row)
        if guid is None:
            logger.info("tombstone_without_guid", source_id=row["id"])
            continue
        candidates[row["id"]] = (guid, row["changeset"])

    held_elsewhere = guids_held_by_other_records(
        {record_id: guid for record_id, (guid, _) in candidates.items()}
    )
    tombstones: list[TombstonedGuid] = []
    for record_id, (guid, changeset_id) in candidates.items():
        if guid in held_elsewhere:
            logger.error(
                "Skipped tombstone: guid is held by another record",
                source_id=record_id,
                guid=guid,
            )
            continue
        tombstones.append(TombstonedGuid(record_id, guid, changeset_id))
    return tombstones


def read_rows(
    changeset_ids: list[str] | None,
    sample_limit: int | None,
    *,
    use_rest_api_table: bool,
) -> tuple[list[dict], list[SupersededGuid], list[TombstonedGuid]]:
    """Read changed Axiell adapter rows, superseded-GUID deletions and
    tombstoned GUIDs.

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
    revert/handoff never suppresses a live record. Tombstones are checked
    against the same mappings (see :func:`tombstoned_guids`). A sample run (no
    changesets) reads active records only, so it has no deletions or
    tombstones, and the reader is built without the facts/reconciler tables.
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
        tombstones = tombstoned_guids(records, reader.guids_held_by_other_records)
        return records, deletions, tombstones

    # Dev/smoke-test fallback: a sample of active records (no changesets given).
    limit = sample_limit or 10
    logger.info("adapter_read", mode="sample", limit=limit)
    rows: list[dict] = []
    for row in reader.iter_records():
        rows.append(row)
        if len(rows) >= limit:
            break
    return rows, [], []
