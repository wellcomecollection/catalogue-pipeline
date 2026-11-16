"""Reads the identified work documents of changed components from the works_identified table."""

import json
from collections.abc import Iterator

import polars as pl
import structlog
from pyiceberg.table import Table as IcebergTable

from adapters.utils.iceberg_lookup import IcebergTableLookup
from models.pipeline.identified.work import IdentifiedWork

logger = structlog.get_logger(__name__)

# Works whose documents are held in memory at once.
GROUP_SIZE = 50_000


def fetch_components(
    table: IcebergTable, snapshot_id: int, changed: pl.DataFrame
) -> Iterator[list[IdentifiedWork]]:
    """Yield the works of each changed component, whole, fetching a group of documents at a time."""
    if changed.is_empty():
        return
    members = changed.group_by("component_id").agg(pl.col("id")).sort("component_id")
    components = [list(ids) for ids in members["id"]]
    lookup = IcebergTableLookup.for_snapshot(table, key="id", snapshot_id=snapshot_id)

    for group in _groups(components):
        documents = _read_documents(lookup, {i for c in group for i in c})
        for component in group:
            works = [
                IdentifiedWork.from_raw_document(json.loads(documents[work_id]))
                for work_id in component
                if work_id in documents
            ]
            if len(works) < len(component):
                logger.warning(
                    "Works missing from the table",
                    missing=[i for i in component if i not in documents],
                )
            yield works


def _groups(components: list[list[str]]) -> Iterator[list[list[str]]]:
    group: list[list[str]] = []
    size = 0
    for component in components:
        group.append(component)
        size += len(component)
        if size >= GROUP_SIZE:
            yield group
            group, size = [], 0
    if group:
        yield group


def _read_documents(lookup: IcebergTableLookup, wanted: set[str]) -> dict[str, str]:
    """The current document of every wanted work: highest source version, then latest write."""
    rows = lookup.fetch(
        wanted, columns=("id", "content"), latest_by=("version", "last_modified")
    )
    logger.info("Read documents", wanted=len(wanted), found=rows.height)
    return dict(zip(rows["id"], rows["content"], strict=True))
