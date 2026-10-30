"""Builders shared by the merger tests: a one-letter-per-work notation for graphs and components."""

from datetime import UTC, datetime
from unittest.mock import MagicMock

import polars as pl
import pytest
import smart_open.s3
from botocore.exceptions import ClientError

from merger.components import COMPONENTS_SCHEMA

# A graph is written as {work: its merge candidates}, one letter per work:
# {"A": "B", "B": "CD"} is A->B, B->C, B->D.
Graph = dict[str, str]

# Components are written as {component id: its members}: {"A": "AB", "C": "C"}.
Components = dict[str, str]

# When each work was last written, by hour; works not listed were written at hour 0.
Written = dict[str, int]

WORKS_SCHEMA: dict = {
    "id": pl.Utf8,
    "version": pl.Int64,
    "last_modified": pl.Datetime("us", "UTC"),
    "type": pl.Utf8,
    "merge_candidate_ids": pl.List(pl.Utf8),
}


def written_at(hour: int) -> datetime:
    return datetime(2026, 1, 1, hour, tzinfo=UTC)


def works(
    graph: Graph, deleted: str = "", written: Written | None = None
) -> pl.DataFrame:
    """A frame of current works in the shape load_works returns."""
    written = written or {}
    return pl.DataFrame(
        {
            "id": list(graph),
            "version": [1] * len(graph),
            "last_modified": [written_at(written.get(work, 0)) for work in graph],
            "type": ["Deleted" if work in deleted else "Visible" for work in graph],
            "merge_candidate_ids": [list(candidates) for candidates in graph.values()],
        },
        schema=WORKS_SCHEMA,
    )


def components(labelled: Components, written: Written | None = None) -> pl.DataFrame:
    """A components frame, as the matcher emits and the previous run stores."""
    written = written or {}
    members = [(work, component) for component, ws in labelled.items() for work in ws]
    return pl.DataFrame(
        {
            "id": [work for work, _ in members],
            "component_id": [component for _, component in members],
            "last_modified": [written_at(written.get(work, 0)) for work, _ in members],
        },
        schema=COMPONENTS_SCHEMA,
    )


def labelled(frame: pl.DataFrame) -> Components:
    """The {component id: members} view of a components frame."""
    grouped = frame.group_by("component_id").agg(pl.col("id").sort().str.join(""))
    return dict(grouped.iter_rows())


def s3_read_error(code: str) -> OSError:
    """The error smart_open itself raises when S3 answers a read with the given code."""
    client = MagicMock()
    client.get_object.side_effect = ClientError({"Error": {"Code": code}}, "GetObject")
    with pytest.raises(OSError) as raised:
        smart_open.s3.open("bucket", "components.parquet", "rb", client=client)
    return raised.value


def iceberg_table(rows: pl.DataFrame, snapshot_id: int = 7) -> MagicMock:
    """A works_identified table whose scan returns the given rows at the given snapshot."""
    table = MagicMock()
    table.current_snapshot.return_value.snapshot_id = snapshot_id
    table.scan.return_value.to_arrow.return_value = rows.to_arrow()
    return table
