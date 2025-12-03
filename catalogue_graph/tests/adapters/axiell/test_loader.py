from __future__ import annotations

from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import cast
from unittest.mock import patch

import pytest
from lxml import etree
from oai_pmh_client.client import OAIClient
from pyiceberg.table import Table as IcebergTable

from adapters.axiell.models.step_events import AxiellAdapterLoaderEvent
from adapters.axiell.steps import loader
from adapters.axiell.steps.loader import LoaderResponse
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_store import WindowStore
from adapters.utils.window_summary import WindowSummary

WINDOW_RANGE = "2025-01-01T10:00:00+00:00-2025-01-01T10:15:00+00:00"


class StubOAIClient(OAIClient):
    def __init__(self) -> None:
        pass


def _request(now: datetime | None = None) -> AxiellAdapterLoaderEvent:
    now = now or datetime.now(tz=UTC)
    return AxiellAdapterLoaderEvent(
        job_id="job-123",
        window_start=now - timedelta(minutes=15),
        window_end=now,
        metadata_prefix="oai",
        set_spec="collect",
        max_windows=5,
    )


def _runtime_with(
    *,
    store: WindowStore | None = None,
    table_client: AdapterStore | None = None,
    oai_client: OAIClient | None = None,
) -> loader.LoaderRuntime:
    if table_client is None:
        pass

    return loader.LoaderRuntime(
        store=cast(WindowStore, store),
        table_client=cast(AdapterStore, table_client),
        oai_client=cast(OAIClient, oai_client or StubOAIClient()),
    )


def test_build_harvester_uses_request_window_minutes(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    req = _request()
    req.window_minutes = 37

    def fake_record_writer(**_: object):  # type: ignore[no-untyped-def]
        return lambda *args, **kwargs: None

    captured: dict[str, int] = {}

    class FakeHarvestManager:
        def __init__(self, *, window_minutes: int, **_: object) -> None:
            captured["window_minutes"] = window_minutes

    monkeypatch.setattr(loader, "WindowRecordWriter", fake_record_writer)
    monkeypatch.setattr(loader, "WindowHarvestManager", FakeHarvestManager)

    runtime = loader.LoaderRuntime.model_construct(
        store=cast(WindowStore, SimpleNamespace()),
        table_client=cast(AdapterStore, SimpleNamespace()),
        oai_client=StubOAIClient(),
    )

    loader.build_harvester(req, runtime)

    assert captured["window_minutes"] == 37


def test_execute_loader_updates_iceberg(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    req = _request()
    summary = WindowSummary.model_validate(
        {
            "window_key": f"{req.window_start.isoformat()}_{req.window_end.isoformat()}",
            "window_start": req.window_start,
            "window_end": req.window_end,
            "state": "success",
            "attempts": 1,
            "record_ids": ["id-1"],
            "last_error": None,
            "updated_at": req.window_end,
            "tags": {
                "job_id": req.job_id,
                "changeset_id": "changeset-123",
                "record_ids_changed": '["id-1"]',
            },
            "changeset_id": "changeset-123",
        }
    )
    table_client = AdapterStore(
        temporary_table, default_namespace=loader.AXIELL_NAMESPACE
    )
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    with patch.object(loader.WindowHarvestManager, "harvest_range") as mock_harvest:
        mock_harvest.return_value = [summary]

        response = loader.execute_loader(req, runtime=runtime)

        assert isinstance(response, LoaderResponse)
        assert response.changeset_ids == ["changeset-123"]
        assert response.changed_record_count == 1
        assert response.job_id == req.job_id
        assert len(response.summaries) == 1

        mock_harvest.assert_called_once_with(
            start_time=req.window_start,
            end_time=req.window_end,
            max_windows=req.max_windows,
            reprocess_successful_windows=False,
        )


def test_execute_loader_counts_only_changed_records(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    req = _request()
    table_client = AdapterStore(
        temporary_table, default_namespace=loader.AXIELL_NAMESPACE
    )
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    summary = WindowSummary.model_validate(
        {
            "window_key": f"{req.window_start.isoformat()}_{req.window_end.isoformat()}",
            "window_start": req.window_start,
            "window_end": req.window_end,
            "state": "success",
            "attempts": 1,
            "record_ids": ["id-1", "id-2"],
            "last_error": None,
            "updated_at": req.window_end,
            "tags": {
                "job_id": req.job_id,
                "changeset_id": "changeset-123",
                "record_ids_changed": '["id-2"]',
            },
            "changeset_id": "changeset-123",
        }
    )

    with patch.object(loader.WindowHarvestManager, "harvest_range") as mock_harvest:
        mock_harvest.return_value = [summary]

        response = loader.execute_loader(req, runtime=runtime)

        assert response.changed_record_count == 1
        assert response.changeset_ids == ["changeset-123"]


def test_execute_loader_handles_no_new_records(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    req = _request()
    table_client = AdapterStore(
        temporary_table, default_namespace=loader.AXIELL_NAMESPACE
    )
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    summary = WindowSummary.model_validate(
        {
            "window_key": f"{req.window_start.isoformat()}_{req.window_end.isoformat()}",
            "window_start": req.window_start,
            "window_end": req.window_end,
            "state": "success",
            "attempts": 1,
            "record_ids": [],
            "last_error": None,
            "updated_at": req.window_end,
            "tags": {"job_id": req.job_id},
            "changeset_id": None,
        }
    )

    with patch.object(loader.WindowHarvestManager, "harvest_range") as mock_harvest:
        mock_harvest.return_value = [summary]

        response = loader.execute_loader(req, runtime=runtime)

        assert response.changeset_ids == []
        assert response.changed_record_count == 0
        assert table_client.get_all_records().num_rows == 0


def test_execute_loader_errors_when_no_windows(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    req = _request()
    table_client = AdapterStore(
        temporary_table, default_namespace=loader.AXIELL_NAMESPACE
    )
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    with patch.object(loader.WindowHarvestManager, "harvest_range") as mock_harvest:
        mock_harvest.return_value = []

        with pytest.raises(RuntimeError):
            loader.execute_loader(req, runtime=runtime)


def test_window_record_writer_persists_window(temporary_table: IcebergTable) -> None:
    table_client = AdapterStore(
        temporary_table, default_namespace=loader.AXIELL_NAMESPACE
    )
    writer = loader.WindowRecordWriter(
        namespace=loader.AXIELL_NAMESPACE,
        table_client=table_client,
        job_id="job-123",
        window_range=WINDOW_RANGE,
    )
    last_modified = datetime(2023, 1, 1, 12, 0, 0, tzinfo=UTC)
    result = writer(
        records=[
            (
                "id-1",
                SimpleNamespace(
                    metadata=etree.fromstring("<metadata />"),
                    header=SimpleNamespace(datestamp=last_modified),
                ),
            )
        ],
    )

    all_records = table_client.get_all_records()
    assert all_records.num_rows == 1
    assert all_records.column("last_modified")[0].as_py() == last_modified

    tags = result["tags"]
    assert tags is not None
    assert tags["job_id"] == "job-123"
    assert tags["window_range"] == WINDOW_RANGE
    assert "changeset_id" in tags


def test_window_record_writer_handles_empty_window(
    temporary_table: IcebergTable,
) -> None:
    table_client = AdapterStore(
        temporary_table, default_namespace=loader.AXIELL_NAMESPACE
    )
    writer = loader.WindowRecordWriter(
        namespace=loader.AXIELL_NAMESPACE,
        table_client=table_client,
        job_id="job-123",
        window_range=WINDOW_RANGE,
    )
    result = writer(
        records=[],
    )

    assert table_client.get_all_records().num_rows == 0
    tags = result["tags"]
    assert tags is not None
    assert tags["job_id"] == "job-123"
    assert tags["window_range"] == WINDOW_RANGE
    assert "changeset_id" not in tags


def test_window_record_writer_handles_deleted_record(
    temporary_table: IcebergTable,
) -> None:
    table_client = AdapterStore(
        temporary_table, default_namespace=loader.AXIELL_NAMESPACE
    )
    writer = loader.WindowRecordWriter(
        namespace=loader.AXIELL_NAMESPACE,
        table_client=table_client,
        job_id="job-123",
        window_range=WINDOW_RANGE,
    )

    last_modified = datetime(2023, 1, 1, 12, 0, 0, tzinfo=UTC)
    writer(
        records=[
            (
                "id-deleted",
                SimpleNamespace(
                    metadata=None, header=SimpleNamespace(datestamp=last_modified)
                ),
            )
        ],
    )

    # Verify soft delete (content is None)
    all_records = table_client.get_all_records(include_deleted=True)
    assert all_records.num_rows == 1
    row = all_records.to_pylist()[0]
    assert row["id"] == "id-deleted"
    assert row["content"] is None
    assert row["last_modified"] == last_modified

    # Verify excluded by default
    active_records = table_client.get_all_records(include_deleted=False)
    assert active_records.num_rows == 0


def test_window_record_writer_skips_changeset_for_duplicate_data(
    temporary_table: IcebergTable,
) -> None:
    table_client = AdapterStore(
        temporary_table, default_namespace=loader.AXIELL_NAMESPACE
    )
    writer = loader.WindowRecordWriter(
        namespace=loader.AXIELL_NAMESPACE,
        table_client=table_client,
        job_id="job-123",
        window_range=WINDOW_RANGE,
    )

    last_modified = datetime(2023, 1, 1, 12, 0, 0, tzinfo=UTC)

    # 1. Write initial data
    result_1 = writer(
        records=[
            (
                "id-1",
                SimpleNamespace(
                    metadata=etree.fromstring("<metadata>v1</metadata>"),
                    header=SimpleNamespace(datestamp=last_modified),
                ),
            )
        ],
    )
    assert "changeset_id" in cast(dict[str, str], result_1["tags"])

    # 2. Write same data again (no-op)
    result_2 = writer(
        records=[
            (
                "id-1",
                SimpleNamespace(
                    metadata=etree.fromstring("<metadata>v1</metadata>"),
                    header=SimpleNamespace(datestamp=last_modified),
                ),
            )
        ],
    )

    # Should have record_ids but NO changeset_id
    tags_2 = cast(dict[str, str], result_2["tags"])
    assert "changeset_id" not in tags_2

    # 3. Write new data
    result_3 = writer(
        records=[
            (
                "id-1",
                SimpleNamespace(
                    metadata=etree.fromstring("<metadata>v2</metadata>"),
                    header=SimpleNamespace(datestamp=last_modified),
                ),
            )
        ],
    )

    # Should have changeset_id again
    assert "changeset_id" in cast(dict[str, str], result_3["tags"])
