from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pyarrow as pa
import pytest

from adapters.axiell.models import LoaderResponse, WindowRequest
from adapters.axiell.steps import loader


class StubTableClient:
    def __init__(self) -> None:
        self.updated_with: pa.Table | None = None
        self.times_called = 0

    def update(self, table: pa.Table) -> str:
        self.updated_with = table
        self.times_called += 1
        return "changeset-123"


class StubHarvester:
    def __init__(self, summaries: list[dict]):
        self.summaries = summaries
        self.calls: list[tuple[datetime, datetime, int | None]] = []

    def harvest_recent(
        self,
        *,
        start_time,
        end_time,
        max_windows,
        reprocess_successful_windows=False,
    ):
        self.calls.append((start_time, end_time, max_windows))
        return self.summaries


def _request(now: datetime | None = None) -> WindowRequest:
    now = now or datetime.now(tz=UTC)
    return WindowRequest(
        window_key="test",
        window_start=now - timedelta(minutes=15),
        window_end=now,
        metadata_prefix="oai",
        set_spec="collect",
        max_windows=5,
    )


def test_execute_loader_updates_iceberg(monkeypatch):
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
    }
    stub_client = StubTableClient()
    runtime = loader.LoaderRuntime(
        store=None, table_client=stub_client, oai_client=None
    )  # type: ignore[arg-type]

    def fake_build_harvester(request, runtime_arg, accumulator):  # noqa: ANN001
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
        fake_build_harvester.instance = harvester
        return harvester

    fake_build_harvester.instance = None  # type: ignore[attr-defined]
    monkeypatch.setattr(loader, "_build_harvester", fake_build_harvester)

    response = loader.execute_loader(req, runtime=runtime)

    assert isinstance(response, LoaderResponse)
    assert response.changeset_id == "changeset-123"
    assert response.record_count == 1
    assert len(response.summaries) == 1
    assert stub_client.updated_with is not None
    assert stub_client.updated_with.to_pylist() == [
        {"namespace": loader.AXIELL_NAMESPACE, "id": "id-1", "content": "<record />"}
    ]

    # confirm harvester invoked with event bounds
    harvester = fake_build_harvester.instance  # type: ignore[assignment]
    assert harvester is not None
    assert harvester.calls == [(req.window_start, req.window_end, req.max_windows)]


def test_execute_loader_handles_no_new_records(monkeypatch):
    req = _request()
    stub_client = StubTableClient()
    runtime = loader.LoaderRuntime(
        store=None, table_client=stub_client, oai_client=None
    )  # type: ignore[arg-type]

    def fake_build_harvester(request, runtime_arg, accumulator):  # noqa: ANN001
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
                }
            ]
        )

    monkeypatch.setattr(loader, "_build_harvester", fake_build_harvester)

    response = loader.execute_loader(req, runtime=runtime)

    assert response.changeset_id is None
    assert response.record_count == 0
    assert stub_client.updated_with is None


def test_execute_loader_errors_when_no_windows(monkeypatch):
    req = _request()
    runtime = loader.LoaderRuntime(
        store=None, table_client=StubTableClient(), oai_client=None
    )  # type: ignore[arg-type]

    def fake_build_harvester(request, runtime_arg, accumulator):  # noqa: ANN001
        return StubHarvester([])

    monkeypatch.setattr(loader, "_build_harvester", fake_build_harvester)

    with pytest.raises(RuntimeError):
        loader.execute_loader(req, runtime=runtime)


def test_execute_loader_filters_delete_warning(monkeypatch):
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
    }
    stub_client = StubTableClient()
    runtime = loader.LoaderRuntime(
        store=None, table_client=stub_client, oai_client=None
    )  # type: ignore[arg-type]

    class WarningHarvester(StubHarvester):
        def harvest_recent(
            self,
            *,
            start_time,
            end_time,
            max_windows,
            reprocess_successful_windows=False,
        ):  # noqa: ANN001
            return super().harvest_recent(
                start_time=start_time,
                end_time=end_time,
                max_windows=max_windows,
                reprocess_successful_windows=reprocess_successful_windows,
            )

    def fake_build_harvester(request, runtime_arg, accumulator):  # noqa: ANN001
        harvester = WarningHarvester([summary])
        accumulator.rows.append(
            {
                "namespace": loader.AXIELL_NAMESPACE,
                "id": "id-1",
                "content": "<record />",
            }
        )
        return harvester

    monkeypatch.setattr(loader, "_build_harvester", fake_build_harvester)

    loader.execute_loader(req, runtime=runtime)

    assert stub_client.times_called == 1
