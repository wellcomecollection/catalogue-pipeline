from __future__ import annotations

from collections.abc import Sequence
from contextlib import suppress
from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import uuid4

from pyiceberg.catalog.sql import SqlCatalog
from pyiceberg.exceptions import NamespaceAlreadyExistsError
from pyiceberg.table import Table as IcebergTable

from adapters.utils.window_reporter import WindowReporter
from adapters.utils.window_store import (
    WINDOW_STATUS_PARTITION_SPEC,
    WINDOW_STATUS_SCHEMA,
    WindowStatusRecord,
    WindowStore,
)


def _create_table(
    catalog_uri: str,
    warehouse_path: Path,
    namespace: str | Sequence[str],
    table_name: str,
    catalog_name: str,
) -> IcebergTable:
    warehouse_path.mkdir(parents=True, exist_ok=True)
    catalog = SqlCatalog(
        name=catalog_name,
        uri=catalog_uri,
        warehouse=str(warehouse_path),
    )
    namespace_tuple = (namespace,) if isinstance(namespace, str) else tuple(namespace)
    with suppress(NamespaceAlreadyExistsError):
        catalog.create_namespace(namespace_tuple)
    identifier = (*namespace_tuple, table_name)
    if catalog.table_exists(identifier):
        return catalog.load_table(identifier)
    return catalog.create_table(
        identifier=identifier,
        schema=WINDOW_STATUS_SCHEMA,
        partition_spec=WINDOW_STATUS_PARTITION_SPEC,
    )


def _build_store(tmp_path: Path) -> WindowStore:
    catalog_path = tmp_path / "catalog.db"
    warehouse_path = tmp_path / "warehouse"
    table = _create_table(
        catalog_uri=f"sqlite:///{catalog_path}",
        warehouse_path=warehouse_path,
        namespace="harvest",
        table_name=f"window_status_{uuid4().hex}",
        catalog_name=f"catalog_{uuid4().hex}",
    )
    return WindowStore(table)


def _insert_window(
    store: WindowStore,
    start: datetime,
    end: datetime,
    state: str = "success",
) -> None:
    from adapters.utils.window_summary import WindowKey

    key = WindowKey.from_dates(start, end)
    store.upsert(
        WindowStatusRecord(
            window_key=key,
            window_start=start,
            window_end=end,
            state=state,
            attempts=1,
            last_error="Error" if state == "failed" else None,
            record_ids=(),
            updated_at=datetime.now(UTC),
            tags=None,
        )
    )


def test_coverage_report(tmp_path: Path) -> None:
    store = _build_store(tmp_path)
    start = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)

    # Insert 2 windows
    _insert_window(store, start, start + timedelta(minutes=15))
    _insert_window(store, start + timedelta(minutes=15), start + timedelta(minutes=30))

    reporter = WindowReporter(store)
    report = reporter.coverage_report()
    assert report.total_windows == 2
    assert report.state_counts.get("success") == 2
    assert report.coverage_hours == 0.5
    assert isinstance(report.coverage_gaps, list)


def test_coverage_report_with_failed_windows(tmp_path: Path) -> None:
    store = _build_store(tmp_path)
    start = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)

    # 12:00 - 12:15 : Success
    _insert_window(store, start, start + timedelta(minutes=15), "success")

    # 12:15 - 12:30 : Failed
    _insert_window(
        store,
        start + timedelta(minutes=15),
        start + timedelta(minutes=30),
        "failed",
    )

    # 12:30 - 12:45 : Success
    _insert_window(
        store,
        start + timedelta(minutes=30),
        start + timedelta(minutes=45),
        "success",
    )

    reporter = WindowReporter(store)
    report = reporter.coverage_report(
        range_start=start, range_end=start + timedelta(minutes=45)
    )

    # Total range is 45 minutes (0.75 hours)
    # Covered range should be 30 minutes (0.5 hours) because of the failure
    assert report.total_windows == 3
    assert report.state_counts["success"] == 2
    assert report.state_counts["failed"] == 1

    assert report.coverage_hours == 0.5

    assert len(report.coverage_gaps) == 1
    gap = report.coverage_gaps[0]
    assert gap.start == start + timedelta(minutes=15)
    assert gap.end == start + timedelta(minutes=30)


def test_coverage_report_with_missing_windows(tmp_path: Path) -> None:
    store = _build_store(tmp_path)
    start = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)

    # 12:00 - 12:15 : Success
    _insert_window(store, start, start + timedelta(minutes=15), "success")

    # 12:15 - 12:30 : Missing

    # 12:30 - 12:45 : Success
    _insert_window(
        store,
        start + timedelta(minutes=30),
        start + timedelta(minutes=45),
        "success",
    )

    reporter = WindowReporter(store)
    report = reporter.coverage_report(
        range_start=start, range_end=start + timedelta(minutes=45)
    )

    assert report.total_windows == 2
    assert report.coverage_hours == 0.5

    assert len(report.coverage_gaps) == 1
    gap = report.coverage_gaps[0]
    assert gap.start == start + timedelta(minutes=15)
    assert gap.end == start + timedelta(minutes=30)


def test_coverage_report_overlapping_success_and_failure(tmp_path: Path) -> None:
    store = _build_store(tmp_path)
    start = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)

    # 12:00 - 13:00 : Failed (60 min window)
    _insert_window(store, start, start + timedelta(minutes=60), "failed")

    # 12:00 - 12:15 : Success (15 min window)
    _insert_window(store, start, start + timedelta(minutes=15), "success")

    reporter = WindowReporter(store)
    report = reporter.coverage_report(
        range_start=start, range_end=start + timedelta(minutes=60)
    )

    # We have 2 windows.
    # 12:00-12:15 is covered.
    # 12:00-13:00 is failed.
    # The coverage should be 15 minutes (0.25 hours).
    # The gap should be 12:15 - 13:00.

    assert report.total_windows == 2
    assert report.coverage_hours == 0.25

    # Gaps:
    # 12:15 - 13:00
    assert len(report.coverage_gaps) == 1
    assert report.coverage_gaps[0].start == start + timedelta(minutes=15)
    assert report.coverage_gaps[0].end == start + timedelta(minutes=60)


def test_coverage_report_last_success_end(tmp_path: Path) -> None:
    store = _build_store(tmp_path)
    start = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)

    # No windows
    reporter = WindowReporter(store)
    report = reporter.coverage_report()
    assert report.last_success_end is None

    # One success
    end1 = start + timedelta(minutes=15)
    _insert_window(store, start, end1, "success")
    report = reporter.coverage_report()
    assert report.last_success_end == end1

    # Followed by failure
    end2 = end1 + timedelta(minutes=15)
    _insert_window(store, end1, end2, "failed")
    report = reporter.coverage_report()
    assert report.last_success_end == end1

    # Followed by another success
    end3 = end2 + timedelta(minutes=15)
    _insert_window(store, end2, end3, "success")
    report = reporter.coverage_report()
    assert report.last_success_end == end3
