from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import Any, cast

import pyarrow as pa
import pytest
from oai_pmh_client.client import OAIClient

from adapters.axiell.models import AxiellAdapterLoaderEvent, LoaderResponse
from adapters.axiell.steps import loader
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
        assert reprocess_successful_windows is True
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
        "tags": {"job_id": req.job_id},
    }
    stub_client = StubTableClient()
    runtime = _runtime_with(table_client=stub_client)

    def fake_build_harvester(
        request: AxiellAdapterLoaderEvent,
        runtime_arg: loader.LoaderRuntime,
        accumulator: loader.RecordAccumulator,
    ) -> StubHarvester:
        assert request.window_key == req.window_key
        harvester = StubHarvester([summary])
        # pre-populate accumulator rows to simulate harvested records
        accumulator.rows.append(
            {
                "namespace": loader.AXIELL_NAMESPACE,
                "id": "id-1",
                "content": "<record />",
            }
        )
        harvester_holder["instance"] = harvester
        return harvester

    harvester_holder: dict[str, StubHarvester | None] = {"instance": None}
    monkeypatch.setattr(loader, "_build_harvester", fake_build_harvester)

    response = loader.execute_loader(req, runtime=runtime)

    assert isinstance(response, LoaderResponse)
    assert response.changeset_id == "changeset-123"
    assert response.record_count == 1
    assert response.job_id == req.job_id
    assert len(response.summaries) == 1
    assert stub_client.updated_with is not None
    assert stub_client.updated_with.to_pylist() == [
        {"namespace": loader.AXIELL_NAMESPACE, "id": "id-1", "content": "<record />"}
    ]

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
        accumulator: loader.RecordAccumulator,
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
                }
            ]
        )

    monkeypatch.setattr(loader, "_build_harvester", fake_build_harvester)

    response = loader.execute_loader(req, runtime=runtime)

    assert response.changeset_id is None
    assert response.record_count == 0
    assert stub_client.updated_with is None


def test_execute_loader_errors_when_no_windows(monkeypatch: pytest.MonkeyPatch) -> None:
    req = _request()
    runtime = _runtime_with(table_client=StubTableClient())

    def fake_build_harvester(
        request: AxiellAdapterLoaderEvent,
        runtime_arg: loader.LoaderRuntime,
        accumulator: loader.RecordAccumulator,
    ) -> StubHarvester:
        return StubHarvester([])

    monkeypatch.setattr(loader, "_build_harvester", fake_build_harvester)

    with pytest.raises(RuntimeError):
        loader.execute_loader(req, runtime=runtime)
