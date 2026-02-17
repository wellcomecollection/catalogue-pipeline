"""Tests for the OAI-PMH adapter loader step.

These tests verify the shared loader implementation used by all OAI-PMH adapters.
Tests are parameterized to run with both Axiell and FOLIO configurations.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import cast
from unittest.mock import MagicMock, patch

import pytest
from lxml import etree

from adapters.oai_pmh.models.step_events import OAIPMHLoaderEvent, OAIPMHLoaderResponse
from adapters.oai_pmh.record_writer import WindowRecordWriter
from adapters.oai_pmh.steps import loader
from adapters.oai_pmh.steps.loader import LoaderRuntime
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_harvester import WindowHarvestManager
from adapters.utils.window_summary import WindowSummary
from models.incremental_window import IncrementalWindow


def _create_loader_event(
    now: datetime | None = None,
    metadata_prefix: str = "oai",
    set_spec: str | None = "collect",
) -> OAIPMHLoaderEvent:
    """Create a loader event for testing."""
    now = now or datetime.now(tz=UTC)
    return OAIPMHLoaderEvent(
        job_id="job-123",
        window=IncrementalWindow(
            start_time=now - timedelta(minutes=15),
            end_time=now,
        ),
        metadata_prefix=metadata_prefix,
        set_spec=set_spec,
        max_windows=5,
    )


def _create_success_summary(
    req: OAIPMHLoaderEvent,
    record_ids: list[str],
    changed_record_ids: list[str] | None = None,
    changeset_id: str | None = "changeset-123",
) -> WindowSummary:
    """Create a success summary for testing."""
    import json

    changed_ids = changed_record_ids or record_ids
    tags: dict[str, str] = {"job_id": req.job_id}
    if changeset_id:
        tags["changeset_id"] = changeset_id
        tags["record_ids_changed"] = json.dumps(changed_ids)

    return WindowSummary.model_validate(
        {
            "window_key": f"{req.window.start_time.isoformat()}_{req.window.end_time.isoformat()}",
            "window_start": req.window.start_time,
            "window_end": req.window.end_time,
            "state": "success",
            "attempts": 1,
            "record_ids": record_ids,
            "last_error": None,
            "updated_at": req.window.end_time,
            "tags": tags,
            "changeset_id": changeset_id,
        }
    )


# ---------------------------------------------------------------------------
# execute_loader tests (parameterized across adapters)
# ---------------------------------------------------------------------------
class TestExecuteLoader:
    def test_updates_iceberg(
        self,
        loader_runtime: LoaderRuntime,
        adapter_name: str,
    ) -> None:
        req = _create_loader_event()
        summary = _create_success_summary(req, record_ids=["id-1"])

        with patch.object(WindowHarvestManager, "harvest_range") as mock_harvest:
            mock_harvest.return_value = [summary]

            response = loader.execute_loader(req, runtime=loader_runtime)

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

    def test_counts_only_changed_records(
        self,
        loader_runtime: LoaderRuntime,
    ) -> None:
        req = _create_loader_event()
        # 2 records seen, but only 1 changed
        summary = _create_success_summary(
            req,
            record_ids=["id-1", "id-2"],
            changed_record_ids=["id-2"],
        )

        with patch.object(WindowHarvestManager, "harvest_range") as mock_harvest:
            mock_harvest.return_value = [summary]

            response = loader.execute_loader(req, runtime=loader_runtime)

            assert response.changed_record_count == 1
            assert response.changeset_ids == ["changeset-123"]

    def test_handles_no_new_records(
        self,
        loader_runtime: LoaderRuntime,
        adapter_store_client: AdapterStore,
    ) -> None:
        req = _create_loader_event()
        summary = _create_success_summary(
            req, record_ids=[], changed_record_ids=[], changeset_id=None
        )
        # Remove changeset_id from tags for no-record case
        summary.tags = {"job_id": req.job_id}

        with patch.object(WindowHarvestManager, "harvest_range") as mock_harvest:
            mock_harvest.return_value = [summary]

            response = loader.execute_loader(req, runtime=loader_runtime)

            assert response.changeset_ids == []
            assert response.changed_record_count == 0
            assert adapter_store_client.get_all_records().num_rows == 0

    def test_errors_when_no_windows(
        self,
        loader_runtime: LoaderRuntime,
    ) -> None:
        req = _create_loader_event()

        with patch.object(WindowHarvestManager, "harvest_range") as mock_harvest:
            mock_harvest.return_value = []

            with pytest.raises(RuntimeError):
                loader.execute_loader(req, runtime=loader_runtime)


# ---------------------------------------------------------------------------
# handler tests (parameterized across adapters)
# ---------------------------------------------------------------------------
class TestHandler:
    def test_publishes_loader_report(
        self,
        loader_runtime: LoaderRuntime,
        adapter_name: str,
    ) -> None:
        req = _create_loader_event()
        summary = _create_success_summary(req, record_ids=["id-1"])

        mock_report = MagicMock()

        with (
            patch.object(WindowHarvestManager, "harvest_range") as mock_harvest,
            patch(
                "adapters.oai_pmh.steps.loader.OAIPMHLoaderReport.from_loader"
            ) as mock_from_loader,
        ):
            mock_harvest.return_value = [summary]
            mock_from_loader.return_value = mock_report

            response = loader.handler(req, runtime=loader_runtime)

        mock_harvest.assert_called_once()
        mock_from_loader.assert_called_once_with(
            req, response, adapter_type=adapter_name
        )
        mock_report.publish.assert_called_once()
        assert response.changed_record_count == 1
        assert response.changeset_ids == ["changeset-123"]


# ---------------------------------------------------------------------------
# WindowRecordWriter tests (adapter-agnostic)
# ---------------------------------------------------------------------------
WINDOW_RANGE = "2025-01-01T10:00:00+00:00-2025-01-01T10:15:00+00:00"


class TestWindowRecordWriter:
    def test_persists_window(
        self,
        adapter_store_client: AdapterStore,
        adapter_namespace: str,
    ) -> None:
        writer = WindowRecordWriter(
            namespace=adapter_namespace,
            table_client=adapter_store_client,
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

        all_records = adapter_store_client.get_all_records()
        assert all_records.num_rows == 1
        assert all_records.column("last_modified")[0].as_py() == last_modified

        tags = result["tags"]
        assert tags is not None
        assert tags["job_id"] == "job-123"
        assert tags["window_range"] == WINDOW_RANGE
        assert "changeset_id" in tags

    def test_handles_empty_window(
        self,
        adapter_store_client: AdapterStore,
        adapter_namespace: str,
    ) -> None:
        writer = WindowRecordWriter(
            namespace=adapter_namespace,
            table_client=adapter_store_client,
            job_id="job-123",
            window_range=WINDOW_RANGE,
        )
        result = writer(records=[])

        assert adapter_store_client.get_all_records().num_rows == 0
        tags = result["tags"]
        assert tags is not None
        assert tags["job_id"] == "job-123"
        assert tags["window_range"] == WINDOW_RANGE
        assert "changeset_id" not in tags

    def test_handles_deleted_record(
        self,
        adapter_store_client: AdapterStore,
        adapter_namespace: str,
    ) -> None:
        writer = WindowRecordWriter(
            namespace=adapter_namespace,
            table_client=adapter_store_client,
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
        all_records = adapter_store_client.get_all_records(include_deleted=True)
        assert all_records.num_rows == 1
        row = all_records.to_pylist()[0]
        assert row["id"] == "id-deleted"
        assert row["content"] is None
        assert row["last_modified"] == last_modified

        # Verify excluded by default
        active_records = adapter_store_client.get_all_records(include_deleted=False)
        assert active_records.num_rows == 0

    def test_preserves_content_on_deletion(
        self,
        adapter_store_client: AdapterStore,
        adapter_namespace: str,
    ) -> None:
        writer = WindowRecordWriter(
            namespace=adapter_namespace,
            table_client=adapter_store_client,
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
        all_records = adapter_store_client.get_all_records(include_deleted=True)
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
        all_records_after = adapter_store_client.get_all_records(include_deleted=True)
        assert all_records_after.num_rows == 1
        row = all_records_after.to_pylist()[0]
        assert row["deleted"] is True
        assert row["content"] == original_content  # Content should be preserved
        assert row["last_modified"] == deletion_time

        # 4. Verify excluded from active records
        active_records = adapter_store_client.get_all_records(include_deleted=False)
        assert active_records.num_rows == 0

    def test_skips_changeset_for_duplicate_data(
        self,
        adapter_store_client: AdapterStore,
        adapter_namespace: str,
    ) -> None:
        writer = WindowRecordWriter(
            namespace=adapter_namespace,
            table_client=adapter_store_client,
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
