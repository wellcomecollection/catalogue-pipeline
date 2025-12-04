from __future__ import annotations

from collections.abc import Sequence
from contextlib import suppress
from datetime import UTC, datetime, timedelta
from functools import wraps
from pathlib import Path
from typing import Any, cast
from uuid import uuid4

import pytest
from _pytest.monkeypatch import MonkeyPatch
from oai_pmh_client.models import Header, Record
from pyiceberg.catalog.sql import SqlCatalog
from pyiceberg.exceptions import NamespaceAlreadyExistsError
from pyiceberg.table import Table as IcebergTable

from adapters.utils.window_generator import WindowGenerator
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
    allow_partial_final_window: bool = True,
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

    from adapters.utils.window_generator import WindowGenerator

    window_generator = WindowGenerator(
        window_minutes=window_minutes or 15,
        allow_partial_final_window=allow_partial_final_window,
    )

    return WindowHarvestManager(
        store=store,
        window_generator=window_generator,
        client=client,
        metadata_prefix="oai_raw",
        set_spec="collect",
        max_parallel_requests=2,
        record_callback=callback,
        default_tags=default_tags,
    )


def _window_range(hours: int = 24) -> tuple[datetime, datetime]:
    start = datetime(2025, 1, 1, tzinfo=UTC)
    return start, start + timedelta(hours=hours)


def test_harvest_range_records_are_stored(tmp_path: Path) -> None:
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
    summaries = harvester.harvest_range(
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
    summaries = harvester.harvest_range(
        start_time=start_time,
        end_time=end_time,
        record_callback=FailingProcessor(),
        max_windows=1,
    )

    assert summaries[0].state == "failed"
    status_map = harvester.store.load_status_map()
    row = next(iter(status_map.values()))
    assert row["state"] == "failed"
    assert row["record_ids"] == []


def test_harvest_range_requires_valid_range(tmp_path: Path) -> None:
    harvester = _build_harvester(tmp_path, [])
    end_time = datetime(2025, 1, 1, tzinfo=UTC)
    with pytest.raises(ValueError):
        harvester.harvest_range(start_time=end_time, end_time=end_time)


def test_harvest_range_skips_successful_windows_by_default(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)

    first = harvester.harvest_range(start_time=start, end_time=end)
    assert len(first) == 1
    client = cast(StubOAIClient, harvester.client)
    initial_calls = len(client.calls)

    second = harvester.harvest_range(start_time=start, end_time=end)
    assert len(second) == 1
    assert second[0].window_key == first[0].window_key
    assert second[0].record_ids == first[0].record_ids
    assert len(client.calls) == initial_calls


def test_harvest_range_can_reprocess_successful_windows(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)

    harvester.harvest_range(start_time=start, end_time=end)
    reprocessed = harvester.harvest_range(
        start_time=start,
        end_time=end,
        reprocess_successful_windows=True,
    )

    assert len(reprocessed) == 1


def test_harvest_range_attaches_default_tags(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(
        tmp_path,
        records,
        default_tags={"job_id": "job-123"},
    )
    start_time, end_time = _window_range(hours=1)

    harvester.harvest_range(
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
    summaries = harvester.harvest_range(
        start_time=start_time,
        end_time=end_time,
        record_callback=RecordingCallback(),
        reprocess_successful_windows=True,
    )

    assert summaries[0].record_ids == ["id:1"]
    assert summaries[0].tags is not None
    assert summaries[0].tags["changeset_id"] == "cs-500"
    status_map = harvester.store.load_status_map()
    stored = next(iter(status_map.values()))
    assert stored["tags"] is not None
    assert stored["tags"]["changeset_id"] == "cs-500"


def test_harvest_range_returns_existing_successful_summary_with_tags(
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

    summaries = harvester.harvest_range(
        start_time=start,
        end_time=end,
        reprocess_successful_windows=False,
    )

    assert len(summaries) == 1
    summary = summaries[0]
    assert summary.window_key == window_key
    assert summary.record_ids == ["existing-1"]
    assert summary.tags is not None
    assert summary.tags["changeset_id"] == "cs-123"


def test_harvest_range_handles_partial_success_across_runs(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes * 2)

    first = harvester.harvest_range(
        start_time=start,
        end_time=end,
        max_windows=1,
    )
    assert len(first) == 1

    second = harvester.harvest_range(
        start_time=start,
        end_time=end,
    )

    assert len(second) == 2
    assert second[0].window_key == first[0].window_key
    assert second[0].record_ids == first[0].record_ids
    assert second[1].window_start > second[0].window_start


def test_harvest_range_reuses_aligned_windows_for_offset_range(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    aligned_start = datetime(2025, 1, 1, tzinfo=UTC)
    aligned_end = aligned_start + timedelta(minutes=harvester.window_minutes * 3)

    harvester.harvest_range(start_time=aligned_start, end_time=aligned_end)
    client = cast(StubOAIClient, harvester.client)
    initial_calls = len(client.calls)

    offset_start = aligned_start + timedelta(minutes=5)
    summaries = harvester.harvest_range(start_time=offset_start, end_time=aligned_end)

    assert len(summaries) == 3
    assert summaries[0].window_start == offset_start
    assert summaries[1].window_start == aligned_start + timedelta(
        minutes=harvester.window_minutes
    )
    assert len(client.calls) - initial_calls == 1


def test_init_with_optional_client(tmp_path: Path) -> None:
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
    window_generator = WindowGenerator()
    manager = WindowHarvestManager(
        store=store, window_generator=window_generator, client=None
    )
    assert manager.client is None
