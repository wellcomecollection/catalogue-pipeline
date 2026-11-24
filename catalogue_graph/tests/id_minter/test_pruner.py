"""Tests for pruning superseded rows from the works_identified table."""

from collections.abc import Generator
from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import uuid1

import pyarrow as pa
import pyarrow.parquet as pq
import pytest
from freezegun import freeze_time
from pyiceberg.expressions import EqualTo
from pyiceberg.io.pyarrow import schema_to_pyarrow
from pyiceberg.table import Table as IcebergTable

import id_minter.pruner as pruner
from adapters.utils.iceberg import LocalIcebergTableConfig, get_local_table
from adapters.utils.iceberg_lookup import IcebergTableLookup
from id_minter.pruner import COMMIT_BACKOFF_MAX_TIME, prune
from id_minter.reporting import PrunerReport
from id_minter.schemata import (
    WORKS_IDENTIFIED_ICEBERG_SCHEMA,
    WORKS_IDENTIFIED_SORT_ORDER,
)
from id_minter.steps import pruner as pruner_step
from merger.matcher import load_works

WRITTEN = datetime(2026, 11, 1, 12, 0, tzinfo=UTC)


@pytest.fixture
def table() -> Generator[IcebergTable]:
    config = LocalIcebergTableConfig(
        table_name=str(uuid1()),
        namespace="test",
        db_name="test_catalog",
        iceberg_schema=WORKS_IDENTIFIED_ICEBERG_SCHEMA,
        sort_order=WORKS_IDENTIFIED_SORT_ORDER,
    )
    table = get_local_table(config)
    try:
        yield table
    finally:
        table.catalog.drop_table(f"test.{config.table_name}")


def _row(work_id: str, version: int, minutes: int = 0) -> dict[str, Any]:
    """A row of a work, written `minutes` after the first write."""
    return {
        "id": work_id,
        "version": version,
        "type": "Visible",
        "source_identifier_type": "sierra-system-number",
        "source_identifier_value": f"b{work_id}",
        "merge_candidate_ids": ["zzzz9999"],
        "content": f'{{"id": "{work_id}", "version": {version}, "minutes": {minutes}}}',
        "last_modified": WRITTEN + timedelta(minutes=minutes),
    }


def _append(table: IcebergTable, *rows: dict[str, Any]) -> None:
    table.append(
        pa.Table.from_pylist(list(rows), schema=schema_to_pyarrow(table.schema()))
    )


def _rows(table: IcebergTable) -> list[dict[str, Any]]:
    """Every row of the table's latest snapshot, ordered by work and write."""
    table.refresh()
    rows = table.scan().to_arrow().to_pylist()
    return sorted(rows, key=lambda row: (row["id"], row["version"], row["content"]))


def _file_paths(table: IcebergTable) -> set[str]:
    table.refresh()
    return {task.file.file_path for task in table.scan().plan_files()}


def _second_handle(table: IcebergTable) -> IcebergTable:
    """The same table as another writer sees it."""
    return table.catalog.load_table(table.name())


class TestPrune:
    def test_empty_table(self, table: IcebergTable) -> None:
        result = prune(table, threshold=0)

        assert result.pruned is False
        assert result.row_count == 0

    def test_keeps_the_row_with_the_highest_version(self, table: IcebergTable) -> None:
        _append(table, _row("aaaa0001", version=1), _row("aaaa0002", version=1))
        _append(table, _row("aaaa0001", version=2, minutes=1))

        result = prune(table, threshold=0)

        assert result.pruned is True
        assert result.row_count == 2
        assert result.removed_row_count == 1
        assert _rows(table) == [
            _row("aaaa0001", version=2, minutes=1),
            _row("aaaa0002", version=1),
        ]

    def test_keeps_the_later_write_of_the_same_version(
        self, table: IcebergTable
    ) -> None:
        _append(table, _row("aaaa0001", version=1))
        _append(table, _row("aaaa0001", version=1, minutes=5))

        prune(table, threshold=0)

        assert _rows(table) == [_row("aaaa0001", version=1, minutes=5)]

    def test_removes_an_older_version_written_later(self, table: IcebergTable) -> None:
        _append(table, _row("aaaa0001", version=2))
        _append(table, _row("aaaa0001", version=1, minutes=5))

        prune(table, threshold=0)

        assert _rows(table) == [_row("aaaa0001", version=2)]

    def test_readers_see_the_same_works(self, table: IcebergTable) -> None:
        _append(table, *[_row(f"aaaa{n:04d}", version=1) for n in range(50)])
        _append(table, *[_row(f"aaaa{n:04d}", version=2, minutes=1) for n in range(20)])
        ids = [f"aaaa{n:04d}" for n in range(50)]

        def read() -> tuple[Any, Any]:
            table.refresh()
            snapshot = table.current_snapshot()
            assert snapshot is not None
            lookup = IcebergTableLookup.for_snapshot(
                table, key="id", snapshot_id=snapshot.snapshot_id
            )
            documents = lookup.fetch(
                ids, columns=("id", "content"), latest_by=("version", "last_modified")
            )
            return (
                load_works(table, snapshot.snapshot_id).sort("id"),
                documents.sort("id"),
            )

        works_before, documents_before = read()
        prune(table, threshold=0)
        works_after, documents_after = read()

        assert works_after.equals(works_before)
        assert documents_after.equals(documents_before)

    def test_leaves_the_table_alone_below_the_threshold(
        self, table: IcebergTable
    ) -> None:
        _append(table, *[_row(f"aaaa{n:04d}", version=1) for n in range(10)])
        _append(table, _row("aaaa0000", version=2, minutes=1))
        snapshot = table.current_snapshot()

        result = prune(table, threshold=0.5)

        assert result.pruned is False
        assert result.row_count == 11
        assert result.superseded_row_count == 1
        table.refresh()
        assert table.current_snapshot() == snapshot

    def test_leaves_the_table_alone_without_superseded_rows(
        self, table: IcebergTable
    ) -> None:
        _append(table, _row("aaaa0001", version=1))
        snapshot = table.current_snapshot()

        result = prune(table, threshold=0)

        assert result.pruned is False
        table.refresh()
        assert table.current_snapshot() == snapshot

    def test_only_replaces_files_holding_superseded_rows(
        self, table: IcebergTable
    ) -> None:
        _append(table, _row("aaaa0001", version=1), _row("aaaa0002", version=1))
        with_superseded = _file_paths(table)
        _append(table, _row("aaaa0001", version=2, minutes=1))
        without_superseded = _file_paths(table) - with_superseded

        result = prune(table, threshold=0)

        assert result.rewritten_file_count == 1
        after = _file_paths(table)
        assert without_superseded <= after
        assert not with_superseded & after
        assert len(after) == 2

    def test_drops_a_file_whose_rows_are_all_superseded(
        self, table: IcebergTable
    ) -> None:
        _append(table, _row("aaaa0001", version=1))
        _append(table, _row("aaaa0001", version=2, minutes=1))
        current = _file_paths(table)

        prune(table, threshold=0)

        assert len(_file_paths(table)) == 1
        assert _file_paths(table) < current

    def test_documents_are_not_dictionary_encoded(self, table: IcebergTable) -> None:
        _append(table, _row("aaaa0001", version=1), _row("aaaa0002", version=1))
        _append(table, _row("aaaa0001", version=2, minutes=1))
        before = _file_paths(table)

        prune(table, threshold=0)

        (path,) = _file_paths(table) - before
        row_group = pq.ParquetFile(path.removeprefix("file://")).metadata.row_group(0)
        encodings = {
            row_group.column(index).path_in_schema: row_group.column(index).encodings
            for index in range(row_group.num_columns)
        }
        assert "RLE_DICTIONARY" not in encodings["content"]
        assert "RLE_DICTIONARY" in encodings["type"]

    def test_splits_a_large_file_into_row_groups(
        self, table: IcebergTable, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setattr(pruner, "ROWS_PER_BATCH", 10)
        monkeypatch.setattr(pruner, "ROWS_PER_ROW_GROUP", 25)
        _append(table, *[_row(f"aaaa{n:04d}", version=1) for n in range(100)])
        _append(table, _row("aaaa0000", version=2, minutes=1))
        before = _file_paths(table)

        prune(table, threshold=0)

        (path,) = _file_paths(table) - before
        metadata = pq.ParquetFile(path.removeprefix("file://")).metadata
        assert metadata.num_rows == 99
        assert metadata.num_row_groups == 4
        assert len(_rows(table)) == 100


class TestPruneAgainstOtherWriters:
    def test_keeps_rows_appended_during_the_rewrite(
        self, table: IcebergTable, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        _append(table, _row("aaaa0001", version=1), _row("aaaa0002", version=1))
        _append(table, _row("aaaa0001", version=2, minutes=1))
        rewrite_file = pruner._rewrite_file

        def rewrite_then_append(*args: Any) -> Any:
            rewritten = rewrite_file(*args)
            _append(_second_handle(table), _row("aaaa0002", version=2, minutes=2))
            return rewritten

        monkeypatch.setattr(pruner, "_rewrite_file", rewrite_then_append)

        result = prune(table, threshold=0)

        assert result.pruned is True
        assert _rows(table) == [
            _row("aaaa0001", version=2, minutes=1),
            _row("aaaa0002", version=1),
            _row("aaaa0002", version=2, minutes=2),
        ]

    def test_retries_a_commit_refused_for_an_append(
        self, table: IcebergTable, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        _append(table, _row("aaaa0001", version=1), _row("aaaa0002", version=1))
        _append(table, _row("aaaa0001", version=2, minutes=1))
        refresh = table.refresh
        late = [_row("aaaa0002", version=2, minutes=2)]

        def refresh_then_append() -> IcebergTable:
            refresh()
            if late:
                _append(_second_handle(table), late.pop())
            return table

        monkeypatch.setattr(table, "refresh", refresh_then_append)
        sleeps: list[float] = []
        monkeypatch.setattr("time.sleep", sleeps.append)

        result = prune(table, threshold=0)

        assert len(sleeps) == 1
        assert result.pruned is True
        assert _rows(table) == [
            _row("aaaa0001", version=2, minutes=1),
            _row("aaaa0002", version=1),
            _row("aaaa0002", version=2, minutes=2),
        ]

    def test_stops_when_a_file_it_did_not_copy_was_replaced(
        self, table: IcebergTable, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        _append(table, _row("aaaa0001", version=1), _row("aaaa0002", version=1))
        _append(table, _row("aaaa0001", version=2, minutes=1))
        _append(table, _row("aaaa0003", version=1))
        rows_before = _rows(table)
        rewrite_file = pruner._rewrite_file

        def rewrite_then_compact(*args: Any) -> Any:
            rewritten = rewrite_file(*args)
            other = _second_handle(table)
            uncopied = other.scan(row_filter=EqualTo("id", "aaaa0003")).to_arrow()
            other.overwrite(uncopied, overwrite_filter=EqualTo("id", "aaaa0003"))
            return rewritten

        monkeypatch.setattr(pruner, "_rewrite_file", rewrite_then_compact)

        result = prune(table, threshold=0)

        assert result.pruned is False
        assert _rows(table) == rows_before

    def test_stops_when_a_file_it_read_was_replaced(
        self, table: IcebergTable, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        _append(table, _row("aaaa0001", version=1), _row("aaaa0002", version=1))
        _append(table, _row("aaaa0001", version=2, minutes=1))
        rows_before = _rows(table)
        rewrite_file = pruner._rewrite_file

        def rewrite_then_compact(*args: Any) -> Any:
            rewritten = rewrite_file(*args)
            other = _second_handle(table)
            other.overwrite(other.scan().to_arrow())
            return rewritten

        monkeypatch.setattr(pruner, "_rewrite_file", rewrite_then_compact)

        result = prune(table, threshold=0)

        assert result.pruned is False
        assert result.superseded_row_count == 1
        assert _rows(table) == rows_before

    def test_stops_when_the_commit_keeps_being_refused(
        self, table: IcebergTable, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        _append(table, _row("aaaa0001", version=1))
        _append(table, _row("aaaa0001", version=2, minutes=1))
        _append(_second_handle(table), _row("aaaa0002", version=1))
        rows_before = _rows(_second_handle(table))
        # The pruner's table never catches up with the other writer.
        monkeypatch.setattr(table, "refresh", lambda: table)

        sleeps: list[float] = []

        def sleep(seconds: float) -> None:
            sleeps.append(seconds)
            clock.tick(seconds)

        with freeze_time() as clock:
            monkeypatch.setattr("time.sleep", sleep)
            result = prune(table, threshold=0)

        assert result.pruned is False
        assert result.superseded_row_count == 1
        assert sum(sleeps) == pytest.approx(COMMIT_BACKOFF_MAX_TIME)
        assert _rows(_second_handle(table)) == rows_before


class TestPrunerStep:
    def test_reports_the_table_after_the_run(
        self, table: IcebergTable, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        _append(table, _row("aaaa0001", version=1), _row("aaaa0002", version=1))
        _append(table, _row("aaaa0001", version=2, minutes=1))
        monkeypatch.setattr(pruner_step, "get_works_identified_table", lambda _: table)
        reports: list[PrunerReport] = []
        monkeypatch.setattr(
            PrunerReport, "publish", lambda report: reports.append(report)
        )

        result = pruner_step.handler()

        assert result.pruned is True
        (report,) = reports
        assert report.publish_to_s3 is False
        assert {metric.name: metric.value for metric in report.metrics} == {
            "row_count": 2,
            "superseded_row_count": 0,
            "superseded_share": 0,
        }

    def test_publishes_the_metrics_the_alarm_reads(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        published: list[tuple[str, dict[str, Any]]] = []

        class Reporter:
            def __init__(self, namespace: str) -> None:
                self.namespace = namespace

            def put_metric_data(self, **metric: Any) -> None:
                published.append((self.namespace, metric))

        monkeypatch.setattr("utils.reporting.MetricReporter", Reporter)

        PrunerReport(
            pipeline_date="2026-09-30",
            row_count=10,
            superseded_row_count=1,
            publish_to_s3=False,
        ).publish()

        dimensions = {
            "pipeline_date": "2026-09-30",
            "pipeline_step": "works_identified_pruner",
        }
        assert len(published) == 3
        for namespace, metric in published:
            assert namespace == "catalogue_graph_pipeline"
            assert metric["dimensions"] == dimensions
        values = {metric["metric_name"]: metric["value"] for _, metric in published}
        assert values == {
            "row_count": 10,
            "superseded_row_count": 1,
            "superseded_share": 0.1,
        }

    def test_reports_no_superseded_share_for_an_empty_table(self) -> None:
        report = PrunerReport(
            pipeline_date="2026-09-30", row_count=0, superseded_row_count=0
        )

        assert report.superseded_share == 0

    def test_does_not_publish_metrics_for_a_dev_pipeline(self) -> None:
        report = PrunerReport(pipeline_date="dev", row_count=1, superseded_row_count=0)

        assert report.publish_to_cloudwatch is False
