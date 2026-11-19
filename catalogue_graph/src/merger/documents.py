"""Reads the identified work documents of changed components from the works_identified table."""

import json

import structlog
from pyiceberg.table import Table as IcebergTable

from adapters.utils.iceberg_lookup import IcebergTableLookup
from models.pipeline.identified.work import IdentifiedWork

logger = structlog.get_logger(__name__)


def read_documents(
    table: IcebergTable, snapshot_id: int, ids: set[str]
) -> dict[str, str]:
    """The current document of every wanted work, from one read of the table: highest
    source version, then latest write."""
    if not ids:
        return {}
    lookup = IcebergTableLookup.for_snapshot(table, key="id", snapshot_id=snapshot_id)
    rows = lookup.fetch(
        ids, columns=("id", "content"), latest_by=("version", "last_modified")
    )
    logger.info("Read documents", wanted=len(ids), found=rows.height)
    return dict(zip(rows["id"], rows["content"], strict=True))


def take_works(documents: dict[str, str], ids: list[str]) -> list[IdentifiedWork]:
    """The works with these ids, parsed from their documents. Each document is removed
    once parsed, as nothing reads it again. Raises when any is missing.

    Every id comes from the snapshot the documents were read at, so a missing one
    means a fault, not a work that changed in the meantime.
    """
    missing = [work_id for work_id in ids if work_id not in documents]
    if missing:
        raise ValueError(f"Works missing from the table: {missing}")
    return [
        IdentifiedWork.from_raw_document(json.loads(documents.pop(work_id)))
        for work_id in ids
    ]
