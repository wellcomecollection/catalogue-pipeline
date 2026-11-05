"""Connected components over merge-candidate links, and which table row counts as current."""

import polars as pl
import pytest

from merger.matcher import GRAPH_COLUMNS, load_works, match_works
from tests.merger.support import (
    WORKS_SCHEMA,
    Components,
    Graph,
    iceberg_table,
    labelled,
    works,
    written_at,
)


@pytest.mark.parametrize(
    ("graph", "deleted", "expected"),
    [
        pytest.param({"A": ""}, "", {"A": "A"}, id="a lone work is its own component"),
        pytest.param({"A": "B", "B": ""}, "", {"A": "AB"}, id="one link"),
        pytest.param({"A": "B", "B": "C", "C": ""}, "", {"A": "ABC"}, id="a chain"),
        pytest.param(
            {"A": "B", "B": "C", "C": "D", "D": ""},
            "",
            {"A": "ABCD"},
            id="two pairs joined by one link",
        ),
        pytest.param(
            {"A": "B", "B": "CD", "C": "", "D": ""},
            "",
            {"A": "ABCD"},
            id="a work with two candidates",
        ),
        pytest.param(
            {"A": "B", "B": "C", "C": "A"},
            "",
            {"A": "ABC"},
            id="a cycle terminates",
        ),
        pytest.param(
            {"B": "A", "A": ""},
            "",
            {"A": "AB"},
            id="the smallest id keys the component even without links of its own",
        ),
        pytest.param(
            {"A": "A", "B": "AA"},
            "",
            {"A": "AB"},
            id="self links and repeated candidates are ignored",
        ),
        pytest.param(
            {"A": "", "B": "", "C": ""},
            "",
            {"A": "A", "B": "B", "C": "C"},
            id="unlinked works stay apart",
        ),
        pytest.param(
            {"A": "Z"},
            "",
            {"A": "A"},
            id="a candidate missing from the table is not emitted",
        ),
        pytest.param(
            {"A": "Z", "B": "Z"},
            "",
            {"A": "AB"},
            id="two works pointing at the same missing candidate are matched",
        ),
        pytest.param(
            {"A": "B", "B": ""},
            "B",
            {"A": "A", "B": "B"},
            id="a deleted target breaks the link",
        ),
        pytest.param(
            {"A": "B", "B": ""},
            "A",
            {"A": "A", "B": "B"},
            id="a deleted source breaks the link",
        ),
        pytest.param(
            {"A": "B", "B": "C", "C": "D", "D": "E", "E": ""},
            "C",
            {"A": "AB", "C": "C", "D": "DE"},
            id="a deleted work splits a chain on either side of it",
        ),
    ],
)
def test_components(graph: Graph, deleted: str, expected: Components) -> None:
    assert labelled(match_works(works(graph, deleted))) == expected


def test_every_work_keeps_its_type_and_write_time() -> None:
    matched = match_works(works({"A": "B", "B": ""}, deleted="B", written={"B": 9}))

    assert matched.columns == ["id", "last_modified", "type", "component_id"]
    assert matched.sort("id")["type"].to_list() == ["Visible", "Deleted"]
    assert matched.sort("id")["last_modified"].to_list() == [
        written_at(0),
        written_at(9),
    ]


def test_no_works_gives_no_components() -> None:
    matched = match_works(works({}))

    assert matched.height == 0
    assert matched.columns == ["id", "last_modified", "type", "component_id"]


# The table is append-only: the same work can have several rows, and the current one
# has the highest version, then the latest write time.
Row = tuple[int, int]  # (version, hour written)


def rows(*rows: Row) -> pl.DataFrame:
    """Several table rows for the one work A."""
    return pl.DataFrame(
        {
            "id": ["A"] * len(rows),
            "version": [version for version, _ in rows],
            "last_modified": [written_at(hour) for _, hour in rows],
            "type": ["Visible"] * len(rows),
            "merge_candidate_ids": [[]] * len(rows),
        },
        schema=WORKS_SCHEMA,
    )


def current_row(*table_rows: Row) -> Row:
    loaded = load_works(iceberg_table(rows(*table_rows)), snapshot_id=1)
    assert loaded.height == 1
    return loaded["version"][0], loaded["last_modified"][0].hour


@pytest.mark.parametrize(
    ("table_rows", "expected"),
    [
        pytest.param([(1, 9)], (1, 9), id="a single row is current"),
        pytest.param([(1, 9), (2, 10)], (2, 10), id="the higher version wins"),
        pytest.param(
            [(2, 9), (1, 10)],
            (2, 9),
            id="the higher version wins even when written earlier",
        ),
        pytest.param(
            [(1, 10), (1, 9)],
            (1, 10),
            id="at the same version the later write wins",
        ),
    ],
)
def test_current_row(table_rows: list[Row], expected: Row) -> None:
    assert current_row(*table_rows) == expected


def test_scan_requests_only_the_graph_columns_at_the_given_snapshot() -> None:
    table = iceberg_table(rows((1, 9)))

    load_works(table, snapshot_id=42)

    table.scan.assert_called_once_with(selected_fields=GRAPH_COLUMNS, snapshot_id=42)
