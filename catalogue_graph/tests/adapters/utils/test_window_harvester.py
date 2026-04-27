from __future__ import annotations

import json
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

import adapters.utils.window_harvester as harvester_mod
from adapters.utils.window_generator import WindowGenerator
from adapters.utils.window_harvester import (
    WindowCallback,
    WindowCallbackResult,
    WindowHarvestManager,
    WindowSummaryTags,
)
from adapters.utils.window_store import (
    WINDOW_STATUS_SCHEMA,
    WindowStore,
)
from adapters.utils.window_summary import WindowSummary
from models.incremental_window import IncrementalWindow

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
        return WindowCallbackResult()


class FailingProcessor:
    def __call__(self, records: list[tuple[str, Record]]) -> WindowCallbackResult:
        raise RuntimeError("boom")


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
        record_callback=callback,
        default_tags=default_tags,
    )


def _window_range(hours: int = 24) -> IncrementalWindow:
    start = datetime(2025, 1, 1, tzinfo=UTC)
    return IncrementalWindow(start_time=start, end_time=start + timedelta(hours=hours))


def test_harvest_range_records_are_stored(tmp_path: Path) -> None:
    records = [_make_record("id:1"), _make_record("id:2")]
    captured: list[str] = []

    class CapturingProcessor(StubWindowProcessor):
        def __call__(
            self,
            records: list[tuple[str, Record]],
        ) -> WindowCallbackResult:
            for identifier, _ in records:
                captured.append(identifier)
            return super().__call__(records)

    harvester = _build_harvester(
        tmp_path,
        records,
        record_callback=CapturingProcessor(),
    )
    window = _window_range()
    summaries = harvester.harvest_range(
        time_range=window,
        max_windows=1,
    )

    assert len(summaries) == 1
    assert captured == ["id:1", "id:2"]
    status_map = harvester.store.load_status_map()
    assert any(
        row.state == "success" and row.record_ids == ["id:1", "id:2"]
        for row in status_map.values()
    )


def test_callback_failure_marks_window_failed(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records, record_callback=FailingProcessor())
    window = _window_range(hours=1)
    summaries = harvester.harvest_range(
        time_range=window,
        max_windows=1,
    )

    assert summaries[0].state == "failed"
    status_map = harvester.store.load_status_map()
    row = next(iter(status_map.values()))
    assert row.state == "failed"
    assert row.record_ids == []


def test_missing_record_identifier_marks_window_failed(tmp_path: Path) -> None:
    # If we can't extract a stable identifier from the OAI-PMH record header,
    # we should fail the window rather than inventing a synthetic ID.
    records = [cast(Record, object())]
    harvester = _build_harvester(tmp_path, records)

    window = _window_range(hours=1)
    summaries = harvester.harvest_range(
        time_range=window,
        max_windows=1,
    )

    assert summaries[0].state == "failed"
    assert summaries[0].record_ids == []
    assert summaries[0].last_error is not None
    assert "header.identifier" in summaries[0].last_error

    status_map = harvester.store.load_status_map()
    row = next(iter(status_map.values()))
    assert row.state == "failed"
    assert row.record_ids == []
    assert row.last_error is not None
    assert "header.identifier" in row.last_error


def test_bad_record_fails_entire_window(tmp_path: Path) -> None:
    # A single bad record should fail the entire window, even if there are
    # valid records present. This ensures we don't silently skip problematic
    # records and lose data.
    records = [_make_record("id:1"), cast(Record, object()), _make_record("id:2")]
    harvester = _build_harvester(tmp_path, records)

    window = _window_range(hours=1)
    summaries = harvester.harvest_range(
        time_range=window,
        max_windows=1,
    )

    assert summaries[0].state == "failed"
    assert summaries[0].record_ids == []
    assert summaries[0].last_error is not None
    assert "header.identifier" in summaries[0].last_error

    status_map = harvester.store.load_status_map()
    row = next(iter(status_map.values()))
    assert row.state == "failed"
    assert row.record_ids == []


def test_harvest_range_requires_valid_range(tmp_path: Path) -> None:
    harvester = _build_harvester(tmp_path, [])
    end_time = datetime(2025, 1, 1, tzinfo=UTC)
    with pytest.raises(ValueError):
        harvester.harvest_range(
            time_range=IncrementalWindow(start_time=end_time, end_time=end_time)
        )


def test_harvest_range_skips_successful_windows_by_default(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)
    window = IncrementalWindow(start_time=start, end_time=end)

    first = harvester.harvest_range(time_range=window)
    assert len(first) == 1
    client = cast(StubOAIClient, harvester.client)
    initial_calls = len(client.calls)

    second = harvester.harvest_range(time_range=window)
    assert len(second) == 1
    assert second[0].window_key == first[0].window_key
    assert second[0].record_ids == first[0].record_ids
    assert len(client.calls) == initial_calls


def test_harvest_range_can_reprocess_successful_windows(tmp_path: Path) -> None:
    records = [_make_record("id:1")]
    harvester = _build_harvester(tmp_path, records)
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)
    window = IncrementalWindow(start_time=start, end_time=end)

    harvester.harvest_range(time_range=window)
    reprocessed = harvester.harvest_range(
        time_range=window,
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
    window = _window_range(hours=1)

    harvester.harvest_range(
        time_range=window,
        max_windows=1,
    )

    status_map = harvester.store.load_status_map()
    assert status_map
    row = next(iter(status_map.values()))
    assert row.tags is not None
    assert row.tags["job_id"] == "job-123"


def test_record_callback_persists_changeset(tmp_path: Path) -> None:
    records = [_make_record("id:1")]

    class RecordingCallback:
        def __call__(
            self,
            records: list[tuple[str, Record]],
        ) -> WindowCallbackResult:
            return WindowCallbackResult(changeset_id="cs-500")

    harvester = _build_harvester(tmp_path, records, record_callback=RecordingCallback())
    window = _window_range(hours=1)
    summaries = harvester.harvest_range(
        time_range=window,
        reprocess_successful_windows=True,
    )

    assert summaries[0].record_ids == ["id:1"]
    assert summaries[0].tags is not None
    assert json.loads(summaries[0].tags["changeset_ids"]) == ["cs-500"]
    status_map = harvester.store.load_status_map()
    stored = next(iter(status_map.values()))
    assert stored.tags is not None
    assert json.loads(stored.tags["changeset_ids"]) == ["cs-500"]


def test_harvest_range_returns_existing_successful_summary_with_tags(
    tmp_path: Path,
) -> None:
    harvester = _build_harvester(tmp_path, [])
    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)
    window_key = f"{start.isoformat()}_{end.isoformat()}"
    harvester.store.upsert(
        WindowSummary(
            window_start=start,
            window_end=end,
            state="success",
            attempts=1,
            last_error=None,
            record_ids=["existing-1"],
            updated_at=end,
            tags={"job_id": "job-1", "changeset_id": "cs-123"},
        )
    )

    summaries = harvester.harvest_range(
        time_range=IncrementalWindow(start_time=start, end_time=end),
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
    window = IncrementalWindow(start_time=start, end_time=end)

    first = harvester.harvest_range(
        time_range=window,
        max_windows=1,
    )
    assert len(first) == 1

    second = harvester.harvest_range(
        time_range=window,
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

    harvester.harvest_range(
        time_range=IncrementalWindow(start_time=aligned_start, end_time=aligned_end)
    )
    client = cast(StubOAIClient, harvester.client)
    initial_calls = len(client.calls)

    offset_start = aligned_start + timedelta(minutes=5)
    summaries = harvester.harvest_range(
        time_range=IncrementalWindow(start_time=offset_start, end_time=aligned_end)
    )

    assert len(summaries) == 3
    assert summaries[0].window_start == offset_start
    assert summaries[1].window_start == aligned_start + timedelta(
        minutes=harvester.window_minutes
    )
    assert len(client.calls) - initial_calls == 1


class BatchTracker:
    """Callback that records per-batch invocations and returns a changeset per batch."""

    def __init__(self, *, fail_on_batch: int | None = None) -> None:
        self.batch_calls: list[list[str]] = []
        self.fail_on_batch = fail_on_batch

    def __call__(
        self,
        records: list[tuple[str, Record]],
    ) -> WindowCallbackResult:
        batch_index = len(self.batch_calls)
        ids = [r[0] for r in records]
        self.batch_calls.append(ids)

        if self.fail_on_batch is not None and batch_index == self.fail_on_batch:
            raise RuntimeError(f"Simulated failure on batch {batch_index}")

        return WindowCallbackResult(
            changeset_id=f"cs-{batch_index}",
            upserted_record_ids=ids,
        )


def test_batching_splits_records_across_callback_invocations(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """With BATCH_SIZE=2 and 5 records we expect 3 callback invocations."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 2)

    records = [_make_record(f"id:{i}") for i in range(5)]
    tracker = BatchTracker()
    harvester = _build_harvester(tmp_path, records, record_callback=tracker)

    summaries = harvester.harvest_range(
        time_range=_window_range(hours=1), max_windows=1
    )

    assert len(tracker.batch_calls) == 3
    assert tracker.batch_calls[0] == ["id:0", "id:1"]
    assert tracker.batch_calls[1] == ["id:2", "id:3"]
    assert tracker.batch_calls[2] == ["id:4"]
    assert summaries[0].state == "success"


def test_batching_accumulates_all_changeset_ids(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """Each batch produces its own changeset_id; the final summary must list them all."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 1)

    records = [_make_record(f"id:{i}") for i in range(3)]
    tracker = BatchTracker()
    harvester = _build_harvester(tmp_path, records, record_callback=tracker)

    summaries = harvester.harvest_range(
        time_range=_window_range(hours=1), max_windows=1
    )

    summary = summaries[0]
    assert summary.tags is not None
    changeset_ids = json.loads(summary.tags["changeset_ids"])
    assert changeset_ids == ["cs-0", "cs-1", "cs-2"]

    # Also verify persisted store
    stored = next(iter(harvester.store.load_status_map().values()))
    assert stored.tags is not None
    assert json.loads(stored.tags["changeset_ids"]) == ["cs-0", "cs-1", "cs-2"]


def test_batching_accumulates_upserted_record_count_tag(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """record_ids_changed tag should sum counts from all batches."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 2)

    records = [_make_record(f"id:{i}") for i in range(4)]
    tracker = BatchTracker()
    harvester = _build_harvester(tmp_path, records, record_callback=tracker)

    summaries = harvester.harvest_range(
        time_range=_window_range(hours=1), max_windows=1
    )

    summary = summaries[0]
    assert summary.tags is not None
    assert int(summary.tags["upserted_record_count"]) == 4


def test_batching_accumulates_record_ids_on_summary(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """The summary's record_ids list should contain IDs from all batches."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 2)

    records = [_make_record(f"id:{i}") for i in range(3)]
    tracker = BatchTracker()
    harvester = _build_harvester(tmp_path, records, record_callback=tracker)

    summaries = harvester.harvest_range(
        time_range=_window_range(hours=1), max_windows=1
    )

    assert summaries[0].record_ids == ["id:0", "id:1", "id:2"]


def test_batching_partial_failure_results_in_partial_success(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """If one batch fails but others succeed, the final state is partial_success."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 1)

    records = [_make_record(f"id:{i}") for i in range(3)]
    tracker = BatchTracker(fail_on_batch=1)
    harvester = _build_harvester(tmp_path, records, record_callback=tracker)

    summaries = harvester.harvest_range(
        time_range=_window_range(hours=1), max_windows=1
    )

    summary = summaries[0]
    assert summary.state == "partial_success"
    assert summary.last_error is not None
    assert "batch 1" in summary.last_error

    # Only successful batches should contribute record_ids
    assert summary.record_ids == ["id:0", "id:2"]

    # Changeset IDs should only include successful batches
    assert summary.tags is not None
    changeset_ids = json.loads(summary.tags["changeset_ids"])
    assert changeset_ids == ["cs-0", "cs-2"]


def test_batching_all_fail_results_in_failed(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """If all batches fail the final state is failed."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 1)

    records = [_make_record("id:0"), _make_record("id:1")]
    harvester = _build_harvester(tmp_path, records, record_callback=FailingProcessor())

    summaries = harvester.harvest_range(
        time_range=_window_range(hours=1), max_windows=1
    )

    assert summaries[0].state == "failed"
    assert summaries[0].record_ids == []
    assert summaries[0].last_error is not None


def test_batching_intermediate_writes_to_store(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """After each successful batch the store is updated with partial_success state."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 1)

    records = [_make_record(f"id:{i}") for i in range(3)]
    store_snapshots: list[WindowSummary] = []
    original_upsert: Any = None

    tracker = BatchTracker()
    harvester = _build_harvester(tmp_path, records, record_callback=tracker)

    original_upsert = harvester.store.upsert

    def capturing_upsert(summary: WindowSummary) -> None:
        store_snapshots.append(summary.model_copy(deep=True))
        original_upsert(summary)

    harvester.store.upsert = capturing_upsert  # type: ignore[assignment]

    harvester.harvest_range(time_range=_window_range(hours=1), max_windows=1)

    # 3 intermediate writes + 1 final write = 4 total upserts
    assert len(store_snapshots) == 4

    # Intermediate snapshots should be partial_success
    assert store_snapshots[0].state == "partial_success"
    assert store_snapshots[0].record_ids == ["id:0"]
    assert store_snapshots[0].tags is not None
    assert json.loads(store_snapshots[0].tags["changeset_ids"]) == ["cs-0"]

    assert store_snapshots[1].state == "partial_success"
    assert store_snapshots[1].record_ids == ["id:0", "id:1"]
    assert store_snapshots[1].tags is not None
    assert json.loads(store_snapshots[1].tags["changeset_ids"]) == ["cs-0", "cs-1"]

    assert store_snapshots[2].state == "partial_success"
    assert store_snapshots[2].record_ids == ["id:0", "id:1", "id:2"]

    # Final write should be success
    assert store_snapshots[3].state == "success"
    assert store_snapshots[3].record_ids == ["id:0", "id:1", "id:2"]


def test_batching_upserted_record_count_grows_across_intermediate_writes(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """upserted_record_count tag in each intermediate write should include all
    IDs from previous batches plus the current one."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 1)

    records = [_make_record(f"id:{i}") for i in range(3)]
    store_snapshots: list[WindowSummary] = []
    tracker = BatchTracker()
    harvester = _build_harvester(tmp_path, records, record_callback=tracker)

    original_upsert = harvester.store.upsert

    def capturing_upsert(summary: WindowSummary) -> None:
        store_snapshots.append(summary.model_copy(deep=True))
        original_upsert(summary)

    harvester.store.upsert = capturing_upsert  # type: ignore[assignment]

    harvester.harvest_range(time_range=_window_range(hours=1), max_windows=1)

    assert store_snapshots[0].tags is not None
    assert int(store_snapshots[0].tags["upserted_record_count"]) == 1
    assert store_snapshots[1].tags is not None
    assert int(store_snapshots[1].tags["upserted_record_count"]) == 2
    assert store_snapshots[2].tags is not None
    assert int(store_snapshots[2].tags["upserted_record_count"]) == 3


def test_batching_with_default_tags_preserved(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """Default tags should be present alongside batching tags in the final summary."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 2)

    records = [_make_record(f"id:{i}") for i in range(3)]
    tracker = BatchTracker()
    harvester = _build_harvester(
        tmp_path,
        records,
        record_callback=tracker,
        default_tags={"job_id": "job-42"},
    )

    summaries = harvester.harvest_range(
        time_range=_window_range(hours=1), max_windows=1
    )

    summary = summaries[0]
    assert summary.tags is not None
    assert summary.tags["job_id"] == "job-42"
    assert json.loads(summary.tags["changeset_ids"]) == ["cs-0", "cs-1"]


def test_partial_success_retry_skips_already_processed_records(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """Re-harvesting a partial_success window should skip records that were
    already processed in the previous run, but the final summary should
    combine record IDs from both runs."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 1)

    records = [_make_record(f"id:{i}") for i in range(3)]
    tracker = BatchTracker(fail_on_batch=1)
    harvester = _build_harvester(tmp_path, records, record_callback=tracker)

    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)
    window = IncrementalWindow(start_time=start, end_time=end)

    # First run: batch 0 succeeds (id:0), batch 1 fails, batch 2 succeeds (id:2)
    first = harvester.harvest_range(time_range=window)
    assert first[0].state == "partial_success"
    assert first[0].record_ids == ["id:0", "id:2"]

    # Second run: a fresh tracker that always succeeds
    tracker2 = BatchTracker()
    harvester.record_callback = tracker2

    second = harvester.harvest_range(time_range=window)
    assert second[0].state == "success"

    # Only id:1 should have been sent to the callback (id:0 and id:2 were skipped)
    assert tracker2.batch_calls == [["id:1"]]

    # Final summary should contain all record IDs (original + retry)
    assert second[0].record_ids == ["id:0", "id:2", "id:1"]

    # Changeset IDs should include both original and retry changesets
    assert second[0].tags is not None
    changeset_ids = json.loads(second[0].tags["changeset_ids"])
    assert changeset_ids == ["cs-0", "cs-2", "cs-0"]


def test_partial_success_retry_processes_all_when_none_succeeded(
    tmp_path: Path, monkeypatch: MonkeyPatch
) -> None:
    """A failed window (no successful batches) should re-process all records."""
    monkeypatch.setattr(harvester_mod, "BATCH_SIZE", 1)

    records = [_make_record("id:0"), _make_record("id:1")]
    harvester = _build_harvester(tmp_path, records, record_callback=FailingProcessor())

    start = datetime(2025, 1, 1, tzinfo=UTC)
    end = start + timedelta(minutes=harvester.window_minutes)
    window = IncrementalWindow(start_time=start, end_time=end)

    first = harvester.harvest_range(time_range=window)
    assert first[0].state == "failed"
    assert first[0].record_ids == []

    # Retry with a working callback - all records should be processed
    tracker = BatchTracker()
    harvester.record_callback = tracker

    second = harvester.harvest_range(time_range=window)
    assert second[0].state == "success"
    assert sorted([rid for batch in tracker.batch_calls for rid in batch]) == [
        "id:0",
        "id:1",
    ]


def test_parse_tags_none_returns_defaults() -> None:
    result = WindowSummaryTags.parse(None)
    assert result.changeset_ids == []
    assert result.upserted_record_count == 0
    assert result.other_tags == {}


def test_parse_tags_only_changeset_id() -> None:
    result = WindowSummaryTags.parse({"changeset_id": "cs-1"})
    assert result.changeset_ids == ["cs-1"]


def test_parse_tags_only_changeset_ids() -> None:
    result = WindowSummaryTags.parse({"changeset_ids": json.dumps(["cs-1", "cs-2"])})
    assert result.changeset_ids == ["cs-1", "cs-2"]


def test_parse_tags_changeset_ids_overrides_changeset_id() -> None:
    result = WindowSummaryTags.parse(
        {"changeset_id": "cs-0", "changeset_ids": json.dumps(["cs-1", "cs-2"])}
    )
    assert result.changeset_ids == ["cs-1", "cs-2"]


def test_parse_tags_record_ids_changed() -> None:
    result = WindowSummaryTags.parse(
        {"record_ids_changed": json.dumps(["a", "b", "c"])}
    )
    assert result.upserted_record_count == 3


def test_parse_tags_upserted_record_count() -> None:
    result = WindowSummaryTags.parse({"upserted_record_count": "7"})
    assert result.upserted_record_count == 7


def test_parse_tags_both_count_formats_prefers_upserted_record_count() -> None:
    result = WindowSummaryTags.parse(
        {
            "record_ids_changed": json.dumps(["a", "b"]),
            "upserted_record_count": "99",
        }
    )
    assert result.upserted_record_count == 99


def test_parse_tags_unknown_keys_passed_through() -> None:
    result = WindowSummaryTags.parse(
        {"changeset_ids": json.dumps([]), "extra": "value"}
    )
    assert result.other_tags == {"extra": "value"}
