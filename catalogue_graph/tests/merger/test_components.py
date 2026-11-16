"""Which components changed since the previous run, and the stored baseline."""

import polars as pl
import pytest

from merger import components as storage
from merger.components import (
    COMPONENTS_SCHEMA,
    find_changed_components,
    read_previous_components,
)
from tests.merger.support import (
    Components,
    Written,
    components,
    labelled,
    s3_read_error,
)
from tests.mocks import MockSmartOpen


def changed(
    previous: Components, current: Components, written: Written | None = None
) -> str:
    """The works reported as changed, as one sorted string."""
    frame = find_changed_components(components(current, written), components(previous))
    return "".join(sorted(frame["id"]))


@pytest.mark.parametrize(
    ("previous", "current", "written", "expected"),
    [
        pytest.param({}, {"A": "AB", "C": "C"}, {}, "ABC", id="no previous run"),
        pytest.param(
            {"A": "AB", "C": "C"}, {"A": "AB", "C": "C"}, {}, "", id="no change"
        ),
        pytest.param(
            {"A": "AB", "C": "C"},
            {"A": "ABC"},
            {},
            "ABC",
            id="a work joins a component",
        ),
        pytest.param(
            {"A": "ABC"},
            {"A": "AB", "C": "C"},
            {},
            "ABC",
            id="a work leaves a component",
        ),
        pytest.param(
            {"A": "ABC"},
            {"A": "A", "B": "BC"},
            {},
            "ABC",
            id="the work whose id keys the component leaves it",
        ),
        pytest.param(
            {"A": "AB", "C": "CD"},
            {"A": "ABD", "C": "C"},
            {},
            "ABCD",
            id="a work moves between components",
        ),
        pytest.param(
            {"A": "AB", "C": "C"},
            {"A": "AB", "C": "C"},
            {"B": 9},
            "AB",
            id="a work written again with no change of shape",
        ),
        pytest.param(
            {"A": "AB", "C": "CD", "E": "E"},
            {"A": "AB", "C": "CD", "E": "E"},
            {"D": 9},
            "CD",
            id="only the component with a change is reported",
        ),
    ],
)
def test_changed_components(
    previous: Components, current: Components, written: Written, expected: str
) -> None:
    assert changed(previous, current, written) == expected


def test_row_order_does_not_matter() -> None:
    previous = components({"A": "ABC"})

    assert find_changed_components(previous.reverse(), previous).height == 0


def test_changed_works_carry_their_current_component_and_write_time() -> None:
    frame = find_changed_components(
        components({"A": "AB"}, written={"B": 9}), components({})
    )

    assert frame.columns == ["id", "component_id", "last_modified"]
    assert labelled(frame) == {"A": "AB"}
    assert frame.sort("id")["last_modified"].dt.hour().to_list() == [0, 9]


URI = "s3://bucket/components.parquet"


def refuse_read(monkeypatch: pytest.MonkeyPatch, code: str) -> None:
    def raise_error(uri: str) -> pl.DataFrame:
        raise s3_read_error(code)

    monkeypatch.setattr(storage, "df_from_s3_parquet", raise_error)


def test_no_stored_baseline_is_empty(monkeypatch: pytest.MonkeyPatch) -> None:
    refuse_read(monkeypatch, "NoSuchKey")

    previous = read_previous_components(URI)

    assert previous.height == 0
    assert previous.schema == pl.Schema(COMPONENTS_SCHEMA)


def test_a_refused_read_is_an_error(monkeypatch: pytest.MonkeyPatch) -> None:
    refuse_read(monkeypatch, "AccessDenied")

    with pytest.raises(OSError, match="AccessDenied"):
        read_previous_components(URI)


def test_stored_baseline_is_read_back() -> None:
    stored = components({"A": "AB", "C": "C"}, written={"B": 9})
    MockSmartOpen.mock_s3_parquet_file(URI, stored)

    assert read_previous_components(URI).equals(stored)


def test_extra_stored_columns_are_dropped() -> None:
    stored = components({"A": "A"}).with_columns(pl.lit("x").alias("extra"))
    MockSmartOpen.mock_s3_parquet_file(URI, stored)

    assert read_previous_components(URI).columns == list(COMPONENTS_SCHEMA)
