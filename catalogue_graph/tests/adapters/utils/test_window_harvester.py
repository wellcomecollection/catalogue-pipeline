from __future__ import annotations

from collections.abc import Sequence
from contextlib import suppress
from datetime import UTC, datetime, timedelta
from functools import wraps
from pathlib import Path
from uuid import uuid4

import pytest
from _pytest.monkeypatch import MonkeyPatch
from oai_pmh_client.models import Header, Record
from pyiceberg.catalog.sql import SqlCatalog
from pyiceberg.exceptions import NamespaceAlreadyExistsError
from pyiceberg.table import Table as IcebergTable

from adapters.utils.window_harvester import WindowHarvestManager
from adapters.utils.window_store import (
    WINDOW_STATUS_PARTITION_SPEC,
    WINDOW_STATUS_SCHEMA,
    IcebergWindowStore,
    WindowStatusRecord,
)

FAST_OAI_BACKOFF_SECONDS = 1e-3


@pytest.fixture(autouse=True)
def fast_oai_backoff(monkeypatch: MonkeyPatch) -> None:
    import oai_pmh_client.client as oai_client

    original_init = oai_client.OAIClient.__init__
    real_sleep = oai_client.time.sleep

    @wraps(original_init)
    def patched_init(self, *args, **kwargs):
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
    store = IcebergWindowStore(table)
    client = StubOAIClient(responses)

    def recorder(
        identifier: str,
        record: Record,
        window_start: datetime,
        window_end: datetime,
        index: int,
    ) -> None:
        return None

    return WindowHarvestManager(
        client=client,
        store=store,
        metadata_prefix="oai_raw",
        set_spec="collect",
        window_minutes=15,
        max_parallel_requests=2,
        record_callback=recorder,
        default_tags=default_tags,
    )


def _window_range(hours: int = 24) -> tuple[datetime, datetime]:
    start = datetime(2025, 1, 1, tzinfo=UTC)
    return start, start + timedelta(hours=hours)


def test_harvest_recent_records_are_stored(tmp_path: Path) -> None:
    records = [_make_record("id:1"), _make_record("id:2")]
    harvester = _build_harvester(tmp_path, records)
    captured: list[str] = []

    def recorder(
        identifier: str,
        record: Record,
        window_start: datetime,
        window_end: datetime,
        index: int,
    ) -> None:
        captured.append(identifier)

    start_time, end_time = _window_range()
    summaries = harvester.harvest_recent(
        start_time=start_time,
        end_time=end_time,
        max_windows=1,
        record_callback=recorder,
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

    def failing_callback(
        identifier: str,
        record: Record,
        window_start: datetime,
        window_end: datetime,
        index: int,
    ) -> None:
        raise RuntimeError("boom")

    start_time, end_time = _window_range(hours=1)
    summaries = harvester.harvest_recent(
        start_time=start_time,
        end_time=end_time,
        record_callback=failing_callback,
        max_windows=1,
    )

    assert summaries[0]["state"] == "failed"
    status_map = harvester.store.load_status_map()
    row = next(iter(status_map.values()))
    assert row["state"] == "failed"
    assert row["record_ids"] == []


def test_retry_failed_windows(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)

    # Seed a failed row manually
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)
    harvester.store.upsert(
        WindowStatusRecord(
            window_key=f"{start.isoformat()}_{end.isoformat()}",
            window_start=start,
            window_end=end,
            state="failed",
            attempts=1,
            last_error="Timeout",
            record_ids=tuple(),
            updated_at=datetime.now(UTC),
        )
    )

    summaries = harvester.retry_failed_windows()
    assert summaries[0]["state"] == "success"


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

    second = harvester.harvest_recent(start_time=start, end_time=end)
    assert second == []


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
