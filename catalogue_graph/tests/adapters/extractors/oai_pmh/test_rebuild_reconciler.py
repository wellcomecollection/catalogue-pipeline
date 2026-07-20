"""Tests for the Axiell reconciler baseline rebuild step."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import cast
from unittest.mock import MagicMock

import pyarrow as pa
import pytest

from adapters.steps.oai_pmh import rebuild_reconciler
from adapters.steps.oai_pmh.rebuild_reconciler import (
    RebuildReconcilerEvent,
    RebuildReconcilerRuntime,
    ReconcilerBaselineBatch,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.reconciler_store import ReconcilerStore
from adapters.utils.schemata import (
    ADAPTER_STORE_ARROW_SCHEMA,
    RECONCILER_STORE_ARROW_SCHEMA,
)


def test_compute_guid_returns_none_for_empty_content() -> None:
    assert (
        rebuild_reconciler._compute_guid({"id": "collect:1", "content": None}) is None
    )
    assert rebuild_reconciler._compute_guid({"id": "collect:1", "content": ""}) is None


def test_compute_guid_returns_none_for_unparseable_content() -> None:
    row = {
        "id": "collect:1",
        "content": "not valid marc xml",
        "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
    }
    assert rebuild_reconciler._compute_guid(row) is None


def test_compute_guid_reads_the_source_identifier_from_marc() -> None:
    # The Axiell source-identifier GUID is the MARC 001 control field, so a real
    # parseable record must round-trip through the builder to that value.
    marc = (
        '<record xmlns="http://www.loc.gov/MARC21/slim">'
        "<leader>00000nam a2200000 a 4500</leader>"
        '<controlfield tag="001">9f9425f7-eaf3-4523-81d1-a1c212650520</controlfield>'
        "</record>"
    )
    row = {
        "id": "collect:15270",
        "content": marc,
        "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
    }
    assert (
        rebuild_reconciler._compute_guid(row) == "9f9425f7-eaf3-4523-81d1-a1c212650520"
    )


def test_compute_guid_returns_none_for_empty_001() -> None:
    # The empty-001 case the July 2026 load left behind must skip, not raise.
    marc = (
        '<record xmlns="http://www.loc.gov/MARC21/slim">'
        "<leader>00000nam a2200000 a 4500</leader>"
        '<controlfield tag="001"></controlfield>'
        "</record>"
    )
    row = {
        "id": "collect:bad",
        "content": marc,
        "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
    }
    assert rebuild_reconciler._compute_guid(row) is None


def _adapter_store_streaming(rows: list[dict]) -> MagicMock:
    now = datetime(2025, 1, 1, tzinfo=UTC)
    full_rows = [
        {
            "namespace": "axiell",
            "id": r["id"],
            "content": r.get("content"),
            "changeset": None,
            "last_modified": now,
            "deleted": False,
        }
        for r in rows
    ]
    batch = pa.Table.from_pylist(full_rows, schema=ADAPTER_STORE_ARROW_SCHEMA)
    reader = pa.RecordBatchReader.from_batches(batch.schema, batch.to_batches())
    store = MagicMock(spec=AdapterStore)
    store.stream_active_namespace_records.return_value = reader
    return store


def _runtime(rows: list[dict]) -> tuple[RebuildReconcilerRuntime, list[pa.Table]]:
    reconciler = MagicMock(spec=ReconcilerStore)
    reconciler.schema = RECONCILER_STORE_ARROW_SCHEMA
    commits: list[pa.Table] = []

    def _update(table: pa.Table) -> MagicMock:
        commits.append(table)
        result = MagicMock()
        result.inserted_record_ids = table["id"].to_pylist()
        result.updated_record_ids = []
        return result

    reconciler.incremental_update.side_effect = _update
    runtime = RebuildReconcilerRuntime(
        adapter_store=_adapter_store_streaming(rows),
        reconciler_store=reconciler,
        adapter_name="axiell",
        namespace="axiell",
    )
    return runtime, commits


class TestRebuildReconcilerEvent:
    def test_parses_a_minimal_event(self) -> None:
        event = RebuildReconcilerEvent.model_validate({"adapter_type": "axiell"})
        assert event.adapter_type == "axiell"
        assert event.batch_size == rebuild_reconciler.DEFAULT_BATCH_SIZE
        assert event.job_id is None

    def test_batch_size_is_coerced_from_a_string(self) -> None:
        event = RebuildReconcilerEvent.model_validate(
            {"adapter_type": "axiell", "batch_size": "500"}
        )
        assert event.batch_size == 500

    def test_rejects_an_event_without_adapter_type(self) -> None:
        with pytest.raises(ValueError):
            RebuildReconcilerEvent.model_validate({})


class TestReconcilerBaselineBatch:
    """The accumulator the handler drives, exercised directly."""

    @staticmethod
    def _row(record_id: str) -> dict:
        return {
            "id": record_id,
            "content": "x",
            "last_modified": datetime(2025, 1, 1, tzinfo=UTC),
        }

    def test_buffers_until_batch_size_then_commits(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setattr(
            rebuild_reconciler, "_compute_guid", lambda row: f"guid-{row['id']}"
        )
        runtime, commits = _runtime([])
        batch = ReconcilerBaselineBatch(runtime, batch_size=2)

        batch.add_row(self._row("collect:1"))
        assert commits == []
        batch.add_row(self._row("collect:2"))
        assert [c.num_rows for c in commits] == [2]
        assert batch.written == 2

    def test_flush_commits_the_remainder(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setattr(
            rebuild_reconciler, "_compute_guid", lambda row: f"guid-{row['id']}"
        )
        runtime, commits = _runtime([])
        batch = ReconcilerBaselineBatch(runtime, batch_size=10)

        batch.add_row(self._row("collect:1"))
        batch.flush()
        assert cast("list[str]", commits[0]["guid"].to_pylist()) == ["guid-collect:1"]

    def test_flush_on_an_empty_buffer_makes_no_commit(self) -> None:
        runtime, commits = _runtime([])
        batch = ReconcilerBaselineBatch(runtime)

        batch.flush()
        batch.flush()
        assert commits == []
        assert batch.written == 0

    def test_rows_without_a_guid_are_skipped_not_buffered(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setattr(rebuild_reconciler, "_compute_guid", lambda row: None)
        runtime, commits = _runtime([])
        batch = ReconcilerBaselineBatch(runtime, batch_size=1)

        batch.add_row(self._row("collect:bad"))
        batch.flush()

        # The row counts as active and skipped, but nothing is written.
        assert batch.active == 1
        assert batch.skipped == 1
        assert commits == []

    def test_written_accumulates_across_flushes(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        # `written` was previously a `nonlocal` in the handler; it must still
        # total across commits rather than reset per flush.
        monkeypatch.setattr(
            rebuild_reconciler, "_compute_guid", lambda row: f"guid-{row['id']}"
        )
        runtime, _ = _runtime([])
        batch = ReconcilerBaselineBatch(runtime, batch_size=1)

        batch.add_row(self._row("collect:1"))
        batch.add_row(self._row("collect:2"))
        assert batch.written == 2

    def test_to_response_reports_the_accumulated_counts(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setattr(
            rebuild_reconciler,
            "_compute_guid",
            lambda row: None if row["id"] == "collect:bad" else f"guid-{row['id']}",
        )
        runtime, _ = _runtime([])
        batch = ReconcilerBaselineBatch(runtime, batch_size=10)

        batch.add_row(self._row("collect:1"))
        batch.add_row(self._row("collect:bad"))
        batch.flush()
        response = batch.to_response()

        assert response.adapter_type == "axiell"
        assert response.active_records == 2
        assert response.mappings_written == 1
        assert response.skipped == 1


def test_handler_skips_and_counts_records_without_guid(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Two records resolve to a GUID; one (empty 001 style) does not.
    def fake_guid(row: dict) -> str | None:
        return None if row["id"] == "collect:bad" else f"guid-{row['id']}"

    monkeypatch.setattr(rebuild_reconciler, "_compute_guid", fake_guid)

    runtime, commits = _runtime(
        [
            {"id": "collect:1", "content": "x"},
            {"id": "collect:bad", "content": "x"},
            {"id": "collect:2", "content": "x"},
        ]
    )
    response = rebuild_reconciler.handler(runtime)

    assert response.active_records == 3
    assert response.mappings_written == 2
    assert response.skipped == 1
    committed_ids = cast("list[str]", commits[0]["id"].to_pylist())
    committed_guids = cast("list[str]", commits[0]["guid"].to_pylist())
    assert sorted(committed_ids) == ["collect:1", "collect:2"]
    assert sorted(committed_guids) == ["guid-collect:1", "guid-collect:2"]


def test_handler_batches_by_batch_size(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        rebuild_reconciler, "_compute_guid", lambda row: f"guid-{row['id']}"
    )
    runtime, commits = _runtime(
        [{"id": f"collect:{i}", "content": "x"} for i in range(5)]
    )
    response = rebuild_reconciler.handler(runtime, batch_size=2)
    assert response.mappings_written == 5
    assert [c.num_rows for c in commits] == [2, 2, 1]
