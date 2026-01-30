from __future__ import annotations

from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import cast
from unittest.mock import MagicMock, patch

import pytest
from lxml import etree
from oai_pmh_client.client import OAIClient
from pyiceberg.table import Table as IcebergTable

from adapters.axiell.runtime import AXIELL_CONFIG
from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent, OAIPMHLoaderResponse
from adapters.oai_pmh.record_writer import WindowRecordWriter
from adapters.oai_pmh.steps import loader
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_harvester import WindowHarvestManager
from adapters.utils.window_store import WindowStore
from adapters.utils.window_summary import WindowSummary
from models.incremental_window import IncrementalWindow

WINDOW_RANGE = "2025-01-01T10:00:00+00:00-2025-01-01T10:15:00+00:00"
AXIELL_NAMESPACE = AXIELL_CONFIG.adapter_namespace


class StubOAIClient(OAIClient):
    def __init__(self) -> None:
        pass


def _request(now: datetime | None = None) -> OAIPMHLoaderEvent:
    now = now or datetime.now(tz=UTC)
    return OAIPMHLoaderEvent(
        job_id="job-123",
        window=IncrementalWindow(
            start_time=now - timedelta(minutes=15),
            end_time=now,
        ),
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
    from adapters.utils.window_generator import WindowGenerator

    if table_client is None:
        pass

    window_generator = WindowGenerator()

    return loader.LoaderRuntime(
        store=cast(WindowStore, store),
        table_client=cast(AdapterStore, table_client),
        oai_client=cast(OAIClient, oai_client or StubOAIClient()),
        window_generator=window_generator,
        adapter_namespace=AXIELL_NAMESPACE,
        adapter_name="axiell",
    )


def test_execute_loader_updates_iceberg(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    req = _request()
    summary = WindowSummary.model_validate(
        {
            "window_key": f"{req.window.start_time.isoformat()}_{req.window.end_time.isoformat()}",
            "window_start": req.window.start_time,
            "window_end": req.window.end_time,
            "state": "success",
            "attempts": 1,
            "record_ids": ["id-1"],
            "last_error": None,
            "updated_at": req.window.end_time,
            "tags": {
                "job_id": req.job_id,
                "changeset_id": "changeset-123",
                "record_ids_changed": '["id-1"]',
            },
            "changeset_id": "changeset-123",
        }
    )
    table_client = AdapterStore(temporary_table, default_namespace=AXIELL_NAMESPACE)
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    with patch.object(WindowHarvestManager, "harvest_range") as mock_harvest:
        mock_harvest.return_value = [summary]

        response = loader.execute_loader(req, runtime=runtime)

        assert isinstance(response, OAIPMHLoaderResponse)
        assert response.changeset_ids == ["changeset-123"]
        assert response.changed_record_count == 1
        assert response.job_id == req.job_id
        assert len(response.summaries) == 1

        mock_harvest.assert_called_once_with(
            start_time=req.window.start_time,
            end_time=req.window.end_time,
            max_windows=req.max_windows,
            reprocess_successful_windows=False,
        )


def test_execute_loader_counts_only_changed_records(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    req = _request()
    table_client = AdapterStore(temporary_table, default_namespace=AXIELL_NAMESPACE)
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    summary = WindowSummary.model_validate(
        {
            "window_key": f"{req.window.start_time.isoformat()}_{req.window.end_time.isoformat()}",
            "window_start": req.window.start_time,
            "window_end": req.window.end_time,
            "state": "success",
            "attempts": 1,
            "record_ids": ["id-1", "id-2"],
            "last_error": None,
            "updated_at": req.window.end_time,
            "tags": {
                "job_id": req.job_id,
                "changeset_id": "changeset-123",
                "record_ids_changed": '["id-2"]',
            },
            "changeset_id": "changeset-123",
        }
    )

    with patch.object(WindowHarvestManager, "harvest_range") as mock_harvest:
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
    table_client = AdapterStore(temporary_table, default_namespace=AXIELL_NAMESPACE)
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    summary = WindowSummary.model_validate(
        {
            "window_key": f"{req.window.start_time.isoformat()}_{req.window.end_time.isoformat()}",
            "window_start": req.window.start_time,
            "window_end": req.window.end_time,
            "state": "success",
            "attempts": 1,
            "record_ids": [],
            "last_error": None,
            "updated_at": req.window.end_time,
            "tags": {"job_id": req.job_id},
            "changeset_id": None,
        }
    )

    with patch.object(WindowHarvestManager, "harvest_range") as mock_harvest:
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
    table_client = AdapterStore(temporary_table, default_namespace=AXIELL_NAMESPACE)
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    with patch.object(WindowHarvestManager, "harvest_range") as mock_harvest:
        mock_harvest.return_value = []

        with pytest.raises(RuntimeError):
            loader.execute_loader(req, runtime=runtime)


def test_handler_publishes_loader_report(
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
) -> None:
    req = _request()
    summary = WindowSummary.model_validate(
        {
            "window_key": f"{req.window.start_time.isoformat()}_{req.window.end_time.isoformat()}",
            "window_start": req.window.start_time,
            "window_end": req.window.end_time,
            "state": "success",
            "attempts": 1,
            "record_ids": ["id-1"],
            "last_error": None,
            "updated_at": req.window.end_time,
            "tags": {
                "job_id": req.job_id,
                "changeset_id": "changeset-123",
                "record_ids_changed": '["id-1"]',
            },
            "changeset_id": "changeset-123",
        }
    )
    table_client = AdapterStore(temporary_table, default_namespace=AXIELL_NAMESPACE)
    store = WindowStore(temporary_window_status_table)
    runtime = _runtime_with(table_client=table_client, store=store)

    mock_report = MagicMock()

    with (
        patch.object(WindowHarvestManager, "harvest_range") as mock_harvest,
        patch(
            "adapters.oai_pmh.steps.loader.OAIPMHLoaderReport.from_loader"
        ) as mock_from_loader,
    ):
        mock_harvest.return_value = [summary]
        mock_from_loader.return_value = mock_report

        response = loader.handler(req, runtime=runtime)

    mock_harvest.assert_called_once_with(
        start_time=req.window.start_time,
        end_time=req.window.end_time,
        max_windows=req.max_windows,
        reprocess_successful_windows=False,
    )
    mock_from_loader.assert_called_once_with(req, response, adapter_type="axiell")
    mock_report.publish.assert_called_once()
    assert response.changed_record_count == 1
    assert response.changeset_ids == ["changeset-123"]


def test_window_record_writer_persists_window(temporary_table: IcebergTable) -> None:
    table_client = AdapterStore(temporary_table, default_namespace=AXIELL_NAMESPACE)
    writer = WindowRecordWriter(
        namespace=AXIELL_NAMESPACE,
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
    table_client = AdapterStore(temporary_table, default_namespace=AXIELL_NAMESPACE)
    writer = WindowRecordWriter(
        namespace=AXIELL_NAMESPACE,
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
    table_client = AdapterStore(temporary_table, default_namespace=AXIELL_NAMESPACE)
    writer = WindowRecordWriter(
        namespace=AXIELL_NAMESPACE,
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


def test_window_record_writer_preserves_content_on_deletion(
    temporary_table: IcebergTable,
) -> None:
    """Test that when an existing record is marked as deleted, its content is preserved."""
    table_client = AdapterStore(temporary_table, default_namespace=AXIELL_NAMESPACE)
    writer = WindowRecordWriter(
        namespace=AXIELL_NAMESPACE,
        table_client=table_client,
        job_id="job-123",
        window_range=WINDOW_RANGE,
    )

    last_modified = datetime(2023, 1, 1, 12, 0, 0, tzinfo=UTC)
    original_content = "<metadata>original content</metadata>"

    # 1. Write initial data with content
    writer(
        records=[
            (
                "id-1",
                SimpleNamespace(
                    metadata=etree.fromstring(original_content),
                    header=SimpleNamespace(datestamp=last_modified),
                ),
            )
        ],
    )

    # Verify initial record exists with content
    all_records = table_client.get_all_records(include_deleted=True)
    assert all_records.num_rows == 1
    initial_row = all_records.to_pylist()[0]
    assert initial_row["content"] == original_content
    assert initial_row["deleted"] is not True

    # 2. Mark the record as deleted (metadata=None)
    deletion_time = last_modified + timedelta(minutes=1)
    writer(
        records=[
            (
                "id-1",
                SimpleNamespace(
                    metadata=None,
                    header=SimpleNamespace(datestamp=deletion_time),
                ),
            )
        ],
    )

    # 3. Verify: record is marked deleted but content is PRESERVED
    all_records_after = table_client.get_all_records(include_deleted=True)
    assert all_records_after.num_rows == 1
    row = all_records_after.to_pylist()[0]
    assert row["deleted"] is True
    assert row["content"] == original_content  # Content should be preserved
    assert row["last_modified"] == deletion_time

    # 4. Verify excluded from active records
    active_records = table_client.get_all_records(include_deleted=False)
    assert active_records.num_rows == 0


def test_window_record_writer_skips_changeset_for_duplicate_data(
    temporary_table: IcebergTable,
) -> None:
    table_client = AdapterStore(temporary_table, default_namespace=AXIELL_NAMESPACE)
    writer = WindowRecordWriter(
        namespace=AXIELL_NAMESPACE,
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
    newer_last_modified = last_modified + timedelta(minutes=1)
    result_3 = writer(
        records=[
            (
                "id-1",
                SimpleNamespace(
                    metadata=etree.fromstring("<metadata>v2</metadata>"),
                    header=SimpleNamespace(datestamp=newer_last_modified),
                ),
            )
        ],
    )

    # Should have changeset_id again
    assert "changeset_id" in cast(dict[str, str], result_3["tags"])
