from __future__ import annotations

from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import Any, cast

import pyarrow as pa
import pytest
from oai_pmh_client.client import OAIClient

from adapters.axiell.models.step_events import AxiellAdapterLoaderEvent
from adapters.axiell.steps import loader
from adapters.axiell.steps.loader import LoaderResponse
from adapters.utils.iceberg import IcebergTableClient
from adapters.utils.window_store import IcebergWindowStore


class StubTableClient:
    def __init__(self) -> None:
        self.updated_with: pa.Table | None = None
        self.times_called = 0

    def update(self, table: pa.Table) -> str:
        self.updated_with = table
        self.times_called += 1
        return "changeset-123"


class StubWindowStore:
    pass


class StubOAIClient:
    pass


class StubHarvester:
    def __init__(self, summaries: list[dict[str, Any]]):
        self.summaries = summaries
        self.calls: list[tuple[datetime, datetime, int | None]] = []

    def harvest_recent(
        self,
        *,
        start_time: datetime,
        end_time: datetime,
        max_windows: int | None,
        reprocess_successful_windows: bool = False,
    ) -> list[dict[str, Any]]:
        self.calls.append((start_time, end_time, max_windows))
        assert reprocess_successful_windows is False
        return self.summaries


def _request(now: datetime | None = None) -> AxiellAdapterLoaderEvent:
    now = now or datetime.now(tz=UTC)
    return AxiellAdapterLoaderEvent(
        job_id="job-123",
        window_key="test",
        window_start=now - timedelta(minutes=15),
        window_end=now,
        metadata_prefix="oai",
        set_spec="collect",
        max_windows=5,
    )


def _runtime_with(
    *,
    store: IcebergWindowStore | None = None,
    table_client: IcebergTableClient | StubTableClient | None = None,
    oai_client: OAIClient | None = None,
) -> loader.LoaderRuntime:
    return loader.LoaderRuntime(
        store=cast(IcebergWindowStore, store or StubWindowStore()),
        table_client=cast(IcebergTableClient, table_client or StubTableClient()),
        oai_client=cast(OAIClient, oai_client or StubOAIClient()),
    )


def test_execute_loader_updates_iceberg(monkeypatch: pytest.MonkeyPatch) -> None:
    req = _request()
    summary = {
        "window_key": req.window_key,
        "window_start": req.window_start,
        "window_end": req.window_end,
        "state": "success",
        "attempts": 1,
        "record_ids": ["id-1"],
        "last_error": None,
        "updated_at": req.window_end,
        "tags": {"job_id": req.job_id, "changeset_id": "changeset-123"},
        "changeset_id": "changeset-123",
    }
    stub_client = StubTableClient()
    runtime = _runtime_with(table_client=stub_client)

    def fake_build_harvester(
        request: AxiellAdapterLoaderEvent,
        runtime_arg: loader.LoaderRuntime,
    ) -> StubHarvester:
        assert request.window_key == req.window_key
        harvester = StubHarvester([summary])
        harvester_holder["instance"] = harvester
        return harvester

    harvester_holder: dict[str, StubHarvester | None] = {"instance": None}
    monkeypatch.setattr(loader, "_build_harvester", fake_build_harvester)

    response = loader.execute_loader(req, runtime=runtime)

    assert isinstance(response, LoaderResponse)
    assert response.changeset_ids == ["changeset-123"]
    assert response.record_count == 1
    assert response.job_id == req.job_id
    assert len(response.summaries) == 1

    # confirm harvester invoked with event bounds
    harvester = harvester_holder["instance"]
    assert harvester is not None
    assert harvester.calls == [(req.window_start, req.window_end, req.max_windows)]


def test_execute_loader_handles_no_new_records(monkeypatch: pytest.MonkeyPatch) -> None:
    req = _request()
    stub_client = StubTableClient()
    runtime = _runtime_with(table_client=stub_client)

    def fake_build_harvester(
        request: AxiellAdapterLoaderEvent,
        runtime_arg: loader.LoaderRuntime,
    ) -> StubHarvester:
        return StubHarvester(
            [
                {
                    "window_key": request.window_key,
                    "window_start": request.window_start,
                    "window_end": request.window_end,
                    "state": "success",
                    "attempts": 1,
                    "record_ids": [],
                    "last_error": None,
                    "updated_at": request.window_end,
                    "tags": {"job_id": request.job_id},
                    "changeset_id": None,
                }
            ]
        )

    monkeypatch.setattr(loader, "_build_harvester", fake_build_harvester)

    response = loader.execute_loader(req, runtime=runtime)

    assert response.changeset_ids == []
    assert response.record_count == 0
    assert stub_client.updated_with is None


def test_execute_loader_errors_when_no_windows(monkeypatch: pytest.MonkeyPatch) -> None:
    req = _request()
    runtime = _runtime_with(table_client=StubTableClient())

    def fake_build_harvester(
        request: AxiellAdapterLoaderEvent,
        runtime_arg: loader.LoaderRuntime,
    ) -> StubHarvester:
        return StubHarvester([])

    monkeypatch.setattr(loader, "_build_harvester", fake_build_harvester)

    with pytest.raises(RuntimeError):
        loader.execute_loader(req, runtime=runtime)


def test_window_record_writer_persists_window() -> None:
    table_client = StubTableClient()
    writer = loader.WindowRecordWriter(
        namespace=loader.AXIELL_NAMESPACE,
        table_client=cast(IcebergTableClient, table_client),
        job_id="job-123",
    )
    window_start = datetime(2025, 1, 1, tzinfo=UTC)
    window_end = window_start + timedelta(minutes=15)
    writer.start_window(
        window_key="key",
        window_start=window_start,
        window_end=window_end,
    )
    writer(
        identifier="id-1",
        record=SimpleNamespace(metadata=None),
        _window_start=window_start,
        _window_end=window_end,
        _index=1,
    )
    result = writer.complete_window(
        window_key="key",
        window_start=window_start,
        window_end=window_end,
        record_ids=["id-1"],
    )

    assert table_client.times_called == 1
    assert result["record_ids"] == ["id-1"]
    tags = result["tags"]
    assert tags is not None
    assert tags["job_id"] == "job-123"
    assert tags["changeset_id"] == "changeset-123"


def test_window_record_writer_handles_empty_window() -> None:
    table_client = StubTableClient()
    writer = loader.WindowRecordWriter(
        namespace=loader.AXIELL_NAMESPACE,
        table_client=cast(IcebergTableClient, table_client),
        job_id="job-123",
    )
    window_start = datetime(2025, 1, 1, tzinfo=UTC)
    window_end = window_start + timedelta(minutes=15)
    writer.start_window(
        window_key="key",
        window_start=window_start,
        window_end=window_end,
    )
    result = writer.complete_window(
        window_key="key",
        window_start=window_start,
        window_end=window_end,
        record_ids=[],
    )

    assert table_client.times_called == 0
    assert result["record_ids"] == []
    tags = result["tags"]
    assert tags is not None
    assert tags["job_id"] == "job-123"
    assert "changeset_id" not in tags
