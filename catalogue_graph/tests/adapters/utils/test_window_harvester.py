from __future__ import annotations

from collections.abc import Sequence
from contextlib import suppress
from datetime import UTC, datetime, timedelta
from functools import wraps
from pathlib import Path
from typing import Any
from uuid import uuid4

import pytest
from _pytest.monkeypatch import MonkeyPatch
from oai_pmh_client.models import Header, Record
from pyiceberg.catalog.sql import SqlCatalog
from pyiceberg.exceptions import NamespaceAlreadyExistsError
from pyiceberg.table import Table as IcebergTable

from adapters.utils.window_harvester import (
    WindowCallback,
    WindowCallbackResult,
    WindowHarvestManager,
)
from adapters.utils.window_store import (
    WINDOW_STATUS_PARTITION_SPEC,
    WINDOW_STATUS_SCHEMA,
    WindowStatusRecord,
    WindowStore,
)

FAST_OAI_BACKOFF_SECONDS = 1e-3


@pytest.fixture(autouse=True)
def fast_oai_backoff(monkeypatch: MonkeyPatch) -> None:
    import oai_pmh_client.client as oai_client

    original_init = oai_client.OAIClient.__init__
    real_sleep = oai_client.time.sleep

    @wraps(original_init)
    def patched_init(self: oai_client.OAIClient, *args: Any, **kwargs: Any) -> None:
        original_init(self, *args, **kwargs)
        self.request_backoff_factor = min(
            self.request_backoff_factor, FAST_OAI_BACKOFF_SECONDS
        )
        self.request_max_backoff = min(
            self.request_max_backoff, FAST_OAI_BACKOFF_SECONDS
        )

    def capped_sleep(delay: float) -> None:
        if delay <= 0:
            return
        real_sleep(min(delay, FAST_OAI_BACKOFF_SECONDS))

    monkeypatch.setattr(oai_client.OAIClient, "__init__", patched_init)
    monkeypatch.setattr(oai_client.time, "sleep", capped_sleep)


class StubOAIClient:
    def __init__(self, responses: list[Record]) -> None:
        self.responses = responses
        self.calls: list[dict[str, object]] = []

    def list_records(self, **kwargs: object) -> list[Record]:
        self.calls.append(kwargs)
        return list(self.responses)


class StubWindowProcessor:
    def __call__(
        self,
        records: list[tuple[str, Record]],
    ) -> WindowCallbackResult:
        return {}


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


def _make_record(identifier: str) -> Record:
    header = Header(
        identifier=identifier,
        datestamp=datetime(2025, 1, 1, tzinfo=UTC),
        setSpec=["collect"],
        status=False,
    )
    return Record(header=header, metadata=None)


def _build_harvester(
    tmp_path: Path,
    responses: list[Record],
    *,
    default_tags: dict[str, str] | None = None,
    record_callback: WindowCallback | None = None,
    window_minutes: int | None = None,
) -> WindowHarvestManager:
    catalog_path = tmp_path / "catalog.db"
    warehouse_path = tmp_path / "warehouse"
    table = _create_table(
        catalog_uri=f"sqlite:///{catalog_path}",
        warehouse_path=warehouse_path,
        namespace="harvest",
        table_name=f"window_status_{uuid4().hex}",
        catalog_name=f"catalog_{uuid4().hex}",
    )
    store = WindowStore(table)
    client = StubOAIClient(responses)

    def default_callback(
        records: list[tuple[str, Record]],
    ) -> WindowCallbackResult:
        return StubWindowProcessor()(records)

    callback = record_callback or default_callback

    return WindowHarvestManager(
        client=client,
        store=store,
        metadata_prefix="oai_raw",
        set_spec="collect",
        window_minutes=window_minutes or 15,
        max_parallel_requests=2,
        record_callback=callback,
        default_tags=default_tags,
    )


def _window_range(hours: int = 24) -> tuple[datetime, datetime]:
    start = datetime(2025, 1, 1, tzinfo=UTC)
    return start, start + timedelta(hours=hours)


@pytest.mark.parametrize(
    "window_minutes, expected",
    [
        (
            15,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 15, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 15, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 45, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 45, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 15, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 15, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 45, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 45, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 55, tzinfo=UTC),
                ),
            ],
        ),
        (
            30,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 12, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 30, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 55, tzinfo=UTC),
                ),
            ],
        ),
        (
            60,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                ),
                (
                    datetime(2025, 1, 1, 13, 0, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 55, tzinfo=UTC),
                ),
            ],
        ),
        (
            240,
            [
                (
                    datetime(2025, 1, 1, 12, 7, tzinfo=UTC),
                    datetime(2025, 1, 1, 13, 55, tzinfo=UTC),
                ),
            ],
        ),
    ],
)
def test_generate_windows_aligns_to_boundaries(
    tmp_path: Path, window_minutes: int, expected: list[tuple[datetime, datetime]]
) -> None:
    harvester = _build_harvester(tmp_path, [], window_minutes=window_minutes)
    start = datetime(2025, 1, 1, 12, 7, tzinfo=UTC)
    end = datetime(2025, 1, 1, 13, 55, tzinfo=UTC)

    windows = harvester.generate_windows(start, end)

    assert windows == expected


def test_harvest_recent_records_are_stored(tmp_path: Path) -> None:
    records = [_make_record("id:1"), _make_record("id:2")]
    harvester = _build_harvester(tmp_path, records)
    captured: list[str] = []

    class CapturingProcessor(StubWindowProcessor):
        def __call__(
            self,
            records: list[tuple[str, Record]],
        ) -> WindowCallbackResult:
            for identifier, _ in records:
                captured.append(identifier)
            return super().__call__(records)

    start_time, end_time = _window_range()
    summaries = harvester.harvest_recent(
        start_time=start_time,
        end_time=end_time,
        max_windows=1,
        record_callback=CapturingProcessor(),
    )

    assert len(summaries) == 1
    assert captured == ["id:1", "id:2"]
    status_map = harvester.store.load_status_map()
    assert any(
        row["state"] == "success" and row["record_ids"] == ["id:1", "id:2"]
        for row in status_map.values()
    )


def test_callback_failure_marks_window_failed(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)

    class FailingProcessor(StubWindowProcessor):
        def __call__(
            self,
            records: list[tuple[str, Record]],
        ) -> WindowCallbackResult:
            raise RuntimeError("boom")

    start_time, end_time = _window_range(hours=1)
    summaries = harvester.harvest_recent(
        start_time=start_time,
        end_time=end_time,
        record_callback=FailingProcessor(),
        max_windows=1,
    )

    assert summaries[0]["state"] == "failed"
    status_map = harvester.store.load_status_map()
    row = next(iter(status_map.values()))
    assert row["state"] == "failed"
    assert row["record_ids"] == []


def test_coverage_report(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    start_time, end_time = _window_range(hours=2)
    harvester.harvest_recent(
        start_time=start_time,
        end_time=end_time,
        max_windows=2,
    )

    report = harvester.coverage_report()
    assert report.total_windows >= 1
    assert report.state_counts.get("success") == report.total_windows
    assert report.coverage_hours > 0
    assert isinstance(report.coverage_gaps, list)


def test_harvest_recent_requires_valid_range(tmp_path: Path) -> None:
    harvester = _build_harvester(tmp_path, [])
    end_time = datetime(2025, 1, 1, tzinfo=UTC)
    with pytest.raises(ValueError):
        harvester.harvest_recent(start_time=end_time, end_time=end_time)


def test_harvest_recent_skips_successful_windows_by_default(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)

    first = harvester.harvest_recent(start_time=start, end_time=end)
    assert len(first) == 1
    initial_calls = len(harvester.client.calls)

    second = harvester.harvest_recent(start_time=start, end_time=end)
    assert len(second) == 1
    assert second[0]["window_key"] == first[0]["window_key"]
    assert second[0]["record_ids"] == first[0]["record_ids"]
    assert len(harvester.client.calls) == initial_calls


def test_harvest_recent_can_reprocess_successful_windows(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)

    harvester.harvest_recent(start_time=start, end_time=end)
    reprocessed = harvester.harvest_recent(
        start_time=start,
        end_time=end,
        reprocess_successful_windows=True,
    )

    assert len(reprocessed) == 1


def test_harvest_recent_attaches_default_tags(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(
        tmp_path,
        records,
        default_tags={"job_id": "job-123"},
    )
    start_time, end_time = _window_range(hours=1)

    harvester.harvest_recent(
        start_time=start_time,
        end_time=end_time,
        max_windows=1,
    )

    status_map = harvester.store.load_status_map()
    assert status_map
    row = next(iter(status_map.values()))
    assert row["tags"] == {"job_id": "job-123"}


def test_record_callback_persists_changeset(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)

    class RecordingCallback:
        def __call__(
            self,
            records: list[tuple[str, Record]],
        ) -> WindowCallbackResult:
            return {
                "tags": {"changeset_id": "cs-500"},
            }

    start_time, end_time = _window_range(hours=1)
    summaries = harvester.harvest_recent(
        start_time=start_time,
        end_time=end_time,
        record_callback=RecordingCallback(),
        reprocess_successful_windows=True,
    )

    assert summaries[0]["record_ids"] == ["id:1"]
    assert summaries[0]["tags"] is not None
    assert summaries[0]["tags"]["changeset_id"] == "cs-500"
    status_map = harvester.store.load_status_map()
    stored = next(iter(status_map.values()))
    assert stored["tags"] is not None
    assert stored["tags"]["changeset_id"] == "cs-500"


def test_harvest_recent_returns_existing_successful_summary_with_tags(
    tmp_path: Path,
) -> None:
    harvester = _build_harvester(tmp_path, [])
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)
    window_key = f"{start.isoformat()}_{end.isoformat()}"
    harvester.store.upsert(
        WindowStatusRecord(
            window_key=window_key,
            window_start=start,
            window_end=end,
            state="success",
            attempts=1,
            last_error=None,
            record_ids=("existing-1",),
            updated_at=end,
            tags={"job_id": "job-1", "changeset_id": "cs-123"},
        )
    )

    summaries = harvester.harvest_recent(
        start_time=start,
        end_time=end,
        reprocess_successful_windows=False,
    )

    assert len(summaries) == 1
    summary = summaries[0]
    assert summary["window_key"] == window_key
    assert summary["record_ids"] == ["existing-1"]
    assert summary["tags"] is not None
    assert summary["tags"]["changeset_id"] == "cs-123"


def test_harvest_recent_handles_partial_success_across_runs(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes * 2)

    first = harvester.harvest_recent(
        start_time=start,
        end_time=end,
        max_windows=1,
    )
    assert len(first) == 1

    second = harvester.harvest_recent(
        start_time=start,
        end_time=end,
    )

    assert len(second) == 2
    assert second[0]["window_key"] == first[0]["window_key"]
    assert second[0]["record_ids"] == first[0]["record_ids"]
    assert second[1]["window_start"] > second[0]["window_start"]


def test_harvest_recent_reuses_aligned_windows_for_offset_range(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    aligned_start = datetime(2025, 1, 1, tzinfo=UTC)
    aligned_end = aligned_start + timedelta(minutes=harvester.window_minutes * 3)

    harvester.harvest_recent(start_time=aligned_start, end_time=aligned_end)
    initial_calls = len(harvester.client.calls)

    offset_start = aligned_start + timedelta(minutes=5)
    summaries = harvester.harvest_recent(start_time=offset_start, end_time=aligned_end)

    assert len(summaries) == 3
    assert summaries[0]["window_start"] == offset_start
    assert summaries[1]["window_start"] == aligned_start + timedelta(
        minutes=harvester.window_minutes
    )
    assert len(harvester.client.calls) - initial_calls == 1


def _insert_window(
    harvester: WindowHarvestManager,
    start: datetime,
    end: datetime,
    state: str = "success",
) -> None:
    key = harvester._window_key(start, end)
    harvester.store.upsert(
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


def test_coverage_report_with_failed_windows(tmp_path: Path) -> None:
    harvester = _build_harvester(tmp_path, [])
    start = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)

    # 12:00 - 12:15 : Success
    _insert_window(harvester, start, start + timedelta(minutes=15), "success")

    # 12:15 - 12:30 : Failed
    _insert_window(
        harvester,
        start + timedelta(minutes=15),
        start + timedelta(minutes=30),
        "failed",
    )

    # 12:30 - 12:45 : Success
    _insert_window(
        harvester,
        start + timedelta(minutes=30),
        start + timedelta(minutes=45),
        "success",
    )

    report = harvester.coverage_report(
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
    harvester = _build_harvester(tmp_path, [])
    start = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)

    # 12:00 - 12:15 : Success
    _insert_window(harvester, start, start + timedelta(minutes=15), "success")

    # 12:15 - 12:30 : Missing

    # 12:30 - 12:45 : Success
    _insert_window(
        harvester,
        start + timedelta(minutes=30),
        start + timedelta(minutes=45),
        "success",
    )

    report = harvester.coverage_report(
        range_start=start, range_end=start + timedelta(minutes=45)
    )

    assert report.total_windows == 2
    assert report.coverage_hours == 0.5

    assert len(report.coverage_gaps) == 1
    gap = report.coverage_gaps[0]
    assert gap.start == start + timedelta(minutes=15)
    assert gap.end == start + timedelta(minutes=30)


def test_coverage_report_overlapping_success_and_failure(tmp_path: Path) -> None:
    harvester = _build_harvester(tmp_path, [])
    start = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)

    # 12:00 - 13:00 : Failed (60 min window)
    _insert_window(harvester, start, start + timedelta(minutes=60), "failed")

    # 12:00 - 12:15 : Success (15 min window)
    _insert_window(harvester, start, start + timedelta(minutes=15), "success")

    report = harvester.coverage_report(
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
