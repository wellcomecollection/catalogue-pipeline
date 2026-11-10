"""Reads the identified work documents of changed components from the works_identified table."""

import json
from collections.abc import Iterator

import polars as pl
import structlog
from pyiceberg.table import Table as IcebergTable

from models.pipeline.identified.work import IdentifiedWork

logger = structlog.get_logger(__name__)

# Works whose documents are held in memory at once.
GROUP_SIZE = 50_000

DOCUMENT_COLUMNS = ("id", "version", "last_modified", "content")


def fetch_components(
    table: IcebergTable, snapshot_id: int, changed: pl.DataFrame
) -> Iterator[list[IdentifiedWork]]:
    """Yield the works of each changed component, whole, fetching a group of documents at a time."""
    members = changed.group_by("component_id").agg(pl.col("id")).sort("component_id")
    components = [list(ids) for ids in members["id"]]
    files = _data_files(table, snapshot_id)

    for group in _groups(components):
        documents = _read_documents(files, {i for c in group for i in c})
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


def _data_files(table: IcebergTable, snapshot_id: int) -> list[str]:
    """The snapshot's data files, as paths polars can scan."""
    files = []
    for task in table.scan(snapshot_id=snapshot_id).plan_files():
        if task.delete_files:
            raise ValueError("The works_identified table is read as append-only")
        files.append(task.file.file_path.removeprefix("file://"))
    return files


def _read_documents(files: list[str], wanted: set[str]) -> dict[str, str]:
    """The current document of every wanted work.

    The files are sorted by id and the compacted ones carry a page index, so a
    filtered scan reads only the pages holding wanted ids instead of every document.
    """
    rows = (
        pl.scan_parquet(files)
        .select(DOCUMENT_COLUMNS)
        .filter(pl.col("id").is_in(list(wanted)))
        .collect(engine="streaming")
    )
    # Same rule as load_works: highest source version, then latest write. Picked on
    # the narrow columns, as sorting the documents too costs more than reading them.
    keys = ["version", "last_modified"]
    current = (
        rows.select("id", *keys)
        .with_row_index()
        .sort(keys, descending=True)
        .unique("id", keep="first")
    )
    rows = rows[current["index"]]
    logger.info("Read documents", wanted=len(wanted), found=rows.height)
    return dict(zip(rows["id"], rows["content"], strict=True))
