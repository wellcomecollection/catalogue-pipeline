"""Reads the identified work documents of changed components from the works_identified table."""

import json
from collections.abc import Iterator
from datetime import datetime
from typing import cast

import polars as pl
import pyarrow as pa
import pyarrow.compute as pc
import structlog
from pyiceberg.io.pyarrow import ArrowScan
from pyiceberg.table import Table as IcebergTable

from models.pipeline.identified.work import IdentifiedWork

logger = structlog.get_logger(__name__)

# Works whose documents are held in memory at once. Each group costs one pass over
# the table, which is cheaper than filtering it: an id filter over random ids still
# decodes every file.
GROUP_SIZE = 200_000


def fetch_components(
    table: IcebergTable, snapshot_id: int, changed: pl.DataFrame
) -> Iterator[list[IdentifiedWork]]:
    """Yield the works of each changed component, whole, reading the table once per group."""
    members = changed.group_by("component_id").agg(pl.col("id")).sort("component_id")
    components = [list(ids) for ids in members["id"]]

    for group in _groups(components):
        documents = _read_documents(table, snapshot_id, {i for c in group for i in c})
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


def _read_documents(
    table: IcebergTable, snapshot_id: int, wanted: set[str]
) -> dict[str, str]:
    """The latest document of every wanted work, from one file-by-file pass over the table."""
    scan = table.scan(
        selected_fields=("id", "last_modified", "content"), snapshot_id=snapshot_id
    )
    arrow_scan = ArrowScan(
        table.metadata,
        table.io,
        scan.projection(),
        scan.row_filter,
        scan.case_sensitive,
    )
    wanted_ids = pa.array(sorted(wanted))

    latest: dict[str, tuple[datetime, str]] = {}
    for task in scan.plan_files():
        for batch in arrow_scan.to_record_batches([task]):
            matches = batch.filter(pc.is_in(batch.column("id"), value_set=wanted_ids))
            for work_id, modified, content in zip(
                cast(list[str], matches.column("id").to_pylist()),
                cast(list[datetime], matches.column("last_modified").to_pylist()),
                cast(list[str], matches.column("content").to_pylist()),
                strict=True,
            ):
                if work_id not in latest or modified > latest[work_id][0]:
                    latest[work_id] = (modified, content)

    logger.info("Read documents", wanted=len(wanted), found=len(latest))
    return {work_id: content for work_id, (_, content) in latest.items()}
