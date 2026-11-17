"""Reads the identified work documents of changed components from the works_identified table."""

import json
from collections.abc import Iterator

import polars as pl
import structlog
from pyiceberg.table import Table as IcebergTable

from adapters.utils.iceberg_lookup import IcebergTableLookup
from models.pipeline.identified.work import IdentifiedWork

logger = structlog.get_logger(__name__)


def fetch_components(
    table: IcebergTable, snapshot_id: int, changed: pl.DataFrame
) -> Iterator[tuple[list[str], list[IdentifiedWork]]]:
    """Yield the ids of each changed component and the works found for them, from one
    read of the table."""
    if changed.is_empty():
        return
    members = changed.group_by("component_id").agg(pl.col("id")).sort("component_id")
    lookup = IcebergTableLookup.for_snapshot(table, key="id", snapshot_id=snapshot_id)
    documents = _read_documents(lookup, set(changed["id"]))

    for ids in members["id"]:
        component = list(ids)
        # Each document is dropped once parsed, so the group's JSON shrinks as it is merged.
        works = [
            IdentifiedWork.from_raw_document(json.loads(documents.pop(work_id)))
            for work_id in component
            if work_id in documents
        ]
        if len(works) < len(component):
            found = {work.state.canonical_id for work in works}
            logger.warning(
                "Works missing from the table",
                missing=[i for i in component if i not in found],
            )
        yield component, works


def _read_documents(lookup: IcebergTableLookup, wanted: set[str]) -> dict[str, str]:
    """The current document of every wanted work: highest source version, then latest write."""
    rows = lookup.fetch(
        wanted, columns=("id", "content"), latest_by=("version", "last_modified")
    )
    logger.info("Read documents", wanted=len(wanted), found=rows.height)
    return dict(zip(rows["id"], rows["content"], strict=True))
