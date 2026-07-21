"""Shared id->GUID mapping computation for Axiell reconciliation.

Reconciliation maps each adapter record id to the source-identifier GUID it
produces, so a later GUID change can mark the old work deleted. This module
holds the guid computation and the adapter-row -> reconciler-mapping
conversion used by the adapter-side reconcile step.
"""

from __future__ import annotations

from collections.abc import Iterable
from typing import Any

import pyarrow as pa

from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from adapters.utils.schemata import RECONCILER_STORE_ARROW_SCHEMA
from utils.marc import parse_single_marc_record


def compute_guid(row: dict[str, Any]) -> str | None:
    """Compute a record's source-identifier GUID, or None if not derivable.

    Follows ``AxiellReconciler._get_record_guid`` (parse the MARCXML, read the
    GUID the Axiell work builder produces), but is intentionally more
    defensive: the builder raises on a record with no usable 001, and here
    that is a skip-and-count rather than a crash, so a whole run is not lost
    to a handful of malformed records.
    """
    content = row.get("content")
    if not content:
        return None
    try:
        record = parse_single_marc_record(content)
    except Exception:
        return None
    try:
        return AxiellWorkBuilder(record, row["last_modified"]).source_identifier.value
    except Exception:
        return None


def rows_to_mappings(
    rows: Iterable[dict[str, Any]], namespace: str
) -> tuple[pa.Table, list[str]]:
    """Convert adapter rows to reconciler-store mapping rows.

    Rows whose guid cannot be derived are skipped. Returns the mappings table
    (reconciler store schema) and the ids of the skipped rows, so callers can
    log and count skips.
    """
    data: list[dict[str, Any]] = []
    skipped_ids: list[str] = []
    for row in rows:
        guid = compute_guid(row)
        if not guid:
            skipped_ids.append(row["id"])
            continue
        data.append(
            {
                "namespace": namespace,
                "id": row["id"],
                "guid": guid,
                "last_modified": row["last_modified"],
            }
        )

    mappings = pa.Table.from_pylist(data, schema=RECONCILER_STORE_ARROW_SCHEMA)
    return mappings, skipped_ids
