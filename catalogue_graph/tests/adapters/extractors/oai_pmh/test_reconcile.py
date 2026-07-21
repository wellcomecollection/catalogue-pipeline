"""Tests for the adapter-side reconcile step.

The step reads the changesets' adapter rows, diffs their id->GUID mappings
against the reconciler store, writes superseded guids as deletion facts first,
and commits the new mappings second.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pyarrow as pa
import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.steps.oai_pmh.reconcile import (
    ReconcileEvent,
    ReconcileResponse,
    ReconcileRuntime,
    build_runtime,
    handler,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from tests.adapters.conftest import reconciler_records_to_table

BASELINE_TIME = datetime(2026, 7, 1, 12, 0, tzinfo=UTC)
UPDATE_TIME = datetime(2026, 7, 2, 12, 0, tzinfo=UTC)

CONTENT_WITHOUT_001 = (
    "<record><leader>00000nam a2200000   4500</leader>"
    "<datafield tag='245' ind1='0' ind2='0'>"
    "<subfield code='a'>Title without GUID</subfield>"
    "</datafield></record>"
)


def _marcxml(guid: str) -> str:
    return (
        "<record><leader>00000nam a2200000   4500</leader>"
        f"<controlfield tag='001'>{guid}</controlfield>"
        "<datafield tag='245' ind1='0' ind2='0'>"
        f"<subfield code='a'>Title for {guid}</subfield>"
        "</datafield></record>"
    )


def _make_runtime(
    temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
) -> ReconcileRuntime:
    return ReconcileRuntime(
        adapter_store=AdapterStore(temporary_table, namespace="axiell"),
        reconciler_store=ReconcilerStore(
            reconciler_temporary_table, namespace="axiell"
        ),
        facts_store=DeletionFactsStore(
            deletion_facts_temporary_table, namespace="axiell"
        ),
        adapter_name="axiell",
        namespace="axiell",
    )


@pytest.fixture
def runtime(
    temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
) -> ReconcileRuntime:
    return _make_runtime(
        temporary_table, reconciler_temporary_table, deletion_facts_temporary_table
    )


def _seed_baseline(runtime: ReconcileRuntime, mappings: dict[str, str]) -> None:
    runtime.reconciler_store.incremental_update(
        reconciler_records_to_table(
            [
                {"id": record_id, "guid": guid, "last_modified": BASELINE_TIME}
                for record_id, guid in mappings.items()
            ],
            namespace="axiell",
        )
    )


def _load_adapter_rows(
    runtime: ReconcileRuntime,
    contents: dict[str, str | None],
    last_modified: datetime = UPDATE_TIME,
) -> str:
    """Upsert adapter rows and return the resulting changeset id."""
    rows = [
        {
            "namespace": "axiell",
            "id": record_id,
            "content": content,
            "changeset": None,
            "last_modified": last_modified,
            "deleted": False,
        }
        for record_id, content in contents.items()
    ]
    changeset = runtime.adapter_store.incremental_update(
        pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)
    )
    assert changeset is not None
    return changeset.changeset_id


def _run(
    runtime: ReconcileRuntime,
    changeset_ids: list[str],
    covered_window_keys: list[str] | None = None,
) -> ReconcileResponse:
    return handler(
        ReconcileEvent(
            job_id="test-job-id",
            adapter_type="axiell",
            changeset_ids=changeset_ids,
            covered_window_keys=covered_window_keys or [],
        ),
        runtime=runtime,
    )


def _fact_rows(runtime: ReconcileRuntime) -> list[dict]:
    return runtime.facts_store.get_namespace_records().to_pylist()


def _mapping_guids(runtime: ReconcileRuntime) -> dict[str, str]:
    return {
        row["id"]: row["guid"]
        for row in runtime.reconciler_store.get_namespace_records().to_pylist()
    }


def test_guid_change_writes_fact_and_updates_mapping(
    runtime: ReconcileRuntime,
) -> None:
    _seed_baseline(runtime, {"collect-1": "guid-old-1"})
    changeset_id = _load_adapter_rows(runtime, {"collect-1": _marcxml("guid-new-1")})

    response = _run(runtime, [changeset_id])

    assert response.facts_written == 1
    assert response.mappings_updated == 1
    assert response.mappings_inserted == 0

    facts = _fact_rows(runtime)
    assert len(facts) == 1
    assert facts[0]["id"] == f"collect-1/{changeset_id}"
    assert facts[0]["record_id"] == "collect-1"
    assert facts[0]["guid"] == "guid-old-1"
    assert facts[0]["changeset"] == changeset_id
    # last_modified comes from the incoming row, not the superseded mapping
    assert facts[0]["last_modified"] == UPDATE_TIME

    assert _mapping_guids(runtime) == {"collect-1": "guid-new-1"}


def test_unchanged_guid_writes_nothing(runtime: ReconcileRuntime) -> None:
    _seed_baseline(runtime, {"collect-1": "guid-1"})
    changeset_id = _load_adapter_rows(runtime, {"collect-1": _marcxml("guid-1")})

    response = _run(runtime, [changeset_id])

    assert response.facts_written == 0
    assert response.mappings_inserted == 0
    assert response.mappings_updated == 0
    assert _fact_rows(runtime) == []
    assert _mapping_guids(runtime) == {"collect-1": "guid-1"}


def test_new_record_inserts_mapping_without_fact(runtime: ReconcileRuntime) -> None:
    changeset_id = _load_adapter_rows(runtime, {"collect-3": _marcxml("guid-3")})

    response = _run(runtime, [changeset_id])

    assert response.facts_written == 0
    assert response.mappings_inserted == 1
    assert response.mappings_updated == 0
    assert _fact_rows(runtime) == []
    assert _mapping_guids(runtime) == {"collect-3": "guid-3"}


def test_unparseable_content_is_skipped_and_counted(
    runtime: ReconcileRuntime,
) -> None:
    changeset_id = _load_adapter_rows(
        runtime,
        {
            "collect-missing-content": None,
            "collect-invalid-xml": "<record><controlfield tag='001'>broken",
            "collect-missing-001": CONTENT_WITHOUT_001,
            "collect-valid": _marcxml("guid-valid"),
        },
    )

    response = _run(runtime, [changeset_id])

    assert response.skipped == 3
    assert response.facts_written == 0
    assert response.mappings_inserted == 1
    assert _mapping_guids(runtime) == {"collect-valid": "guid-valid"}


def test_guid_handoff_to_incoming_record_fails_the_run(
    runtime: ReconcileRuntime,
) -> None:
    """Record B's incoming mapping claims record A's old guid: a handoff can
    only come from a source data quality issue, so the run stops before any
    facts or mappings are written."""
    _seed_baseline(runtime, {"collect-1": "guid-shared"})
    changeset_id = _load_adapter_rows(
        runtime,
        {
            "collect-1": _marcxml("guid-new-1"),
            "collect-2": _marcxml("guid-shared"),
        },
    )

    with pytest.raises(ValueError, match="guid handoff"):
        _run(runtime, [changeset_id])

    assert _fact_rows(runtime) == []
    assert _mapping_guids(runtime) == {"collect-1": "guid-shared"}


def test_guid_claimed_by_another_stored_record_fails_the_run(
    runtime: ReconcileRuntime,
) -> None:
    """A different record already holds the old guid as its current mapping in
    the store: two records carried the same guid, so the run stops."""
    _seed_baseline(runtime, {"collect-1": "guid-shared", "collect-2": "guid-shared"})
    changeset_id = _load_adapter_rows(runtime, {"collect-1": _marcxml("guid-new-1")})

    with pytest.raises(ValueError, match="guid handoff"):
        _run(runtime, [changeset_id])

    assert _fact_rows(runtime) == []
    assert _mapping_guids(runtime) == {
        "collect-1": "guid-shared",
        "collect-2": "guid-shared",
    }


def test_all_claimants_leaving_a_shared_guid_write_facts(
    runtime: ReconcileRuntime,
) -> None:
    """Two records sharing a stored guid (e.g. duplicate 001s) are both
    re-identified in one run: nothing claims the guid post-commit, so both
    facts are written rather than each tripping the handoff check."""
    _seed_baseline(runtime, {"collect-1": "guid-shared", "collect-2": "guid-shared"})
    changeset_id = _load_adapter_rows(
        runtime,
        {
            "collect-1": _marcxml("guid-new-1"),
            "collect-2": _marcxml("guid-new-2"),
        },
    )

    response = _run(runtime, [changeset_id])

    assert response.facts_written == 2
    assert response.mappings_updated == 2

    facts = _fact_rows(runtime)
    assert {row["record_id"] for row in facts} == {"collect-1", "collect-2"}
    assert {row["guid"] for row in facts} == {"guid-shared"}
    assert _mapping_guids(runtime) == {
        "collect-1": "guid-new-1",
        "collect-2": "guid-new-2",
    }


def test_timestamp_gated_claimant_still_fails_the_run(
    runtime: ReconcileRuntime,
) -> None:
    """A stored claimant whose own incoming update is older than its mapping
    keeps its guid post-commit, so the departing record's change is still a
    handoff and stops the run."""
    _seed_baseline(runtime, {"collect-1": "guid-shared", "collect-2": "guid-shared"})
    departing_changeset = _load_adapter_rows(
        runtime, {"collect-1": _marcxml("guid-new-1")}
    )
    stale_changeset = _load_adapter_rows(
        runtime,
        {"collect-2": _marcxml("guid-new-2")},
        last_modified=BASELINE_TIME - timedelta(days=1),
    )

    with pytest.raises(ValueError, match="guid handoff"):
        _run(runtime, [departing_changeset, stale_changeset])

    assert _fact_rows(runtime) == []
    assert _mapping_guids(runtime) == {
        "collect-1": "guid-shared",
        "collect-2": "guid-shared",
    }


def test_retry_after_mappings_failure_converges_without_duplicate_facts(
    runtime: ReconcileRuntime,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _seed_baseline(runtime, {"collect-1": "guid-old-1"})
    changeset_id = _load_adapter_rows(runtime, {"collect-1": _marcxml("guid-new-1")})

    def failing_update(new_data: pa.Table) -> None:
        raise RuntimeError("simulated mappings commit failure")

    monkeypatch.setattr(runtime.reconciler_store, "incremental_update", failing_update)
    with pytest.raises(RuntimeError, match="simulated mappings commit failure"):
        _run(runtime, [changeset_id])

    # Facts landed but the mapping did not change
    assert len(_fact_rows(runtime)) == 1
    assert _mapping_guids(runtime) == {"collect-1": "guid-old-1"}

    monkeypatch.undo()
    retry = _run(runtime, [changeset_id])

    assert retry.facts_written == 0  # deduplicated on the deterministic fact id
    assert retry.mappings_updated == 1
    assert len(_fact_rows(runtime)) == 1
    assert _mapping_guids(runtime) == {"collect-1": "guid-new-1"}

    # A further retry with both writes committed is a complete no-op
    settled = _run(runtime, [changeset_id])
    assert settled.facts_written == 0
    assert settled.mappings_updated == 0
    assert len(_fact_rows(runtime)) == 1


def test_facts_are_tagged_with_the_changeset_their_record_arrived_in(
    runtime: ReconcileRuntime,
) -> None:
    _seed_baseline(runtime, {"collect-1": "guid-old-1", "collect-2": "guid-old-2"})
    first_changeset = _load_adapter_rows(runtime, {"collect-1": _marcxml("guid-new-1")})
    second_changeset = _load_adapter_rows(
        runtime,
        {"collect-2": _marcxml("guid-new-2")},
        last_modified=UPDATE_TIME + timedelta(minutes=15),
    )

    response = _run(runtime, [first_changeset, second_changeset])

    assert response.facts_written == 2
    changesets_by_record = {
        row["record_id"]: row["changeset"] for row in _fact_rows(runtime)
    }
    assert changesets_by_record == {
        "collect-1": first_changeset,
        "collect-2": second_changeset,
    }
    fact_ids = {row["id"] for row in _fact_rows(runtime)}
    assert fact_ids == {
        f"collect-1/{first_changeset}",
        f"collect-2/{second_changeset}",
    }


def test_response_echoes_event_fields(runtime: ReconcileRuntime) -> None:
    changeset_id = _load_adapter_rows(runtime, {"collect-1": _marcxml("guid-1")})
    window_keys = ["2026-07-02T12:00:00+00:00/PT15M"]

    response = _run(runtime, [changeset_id], covered_window_keys=window_keys)

    assert response.job_id == "test-job-id"
    assert response.adapter_type == "axiell"
    assert response.changeset_ids == [changeset_id]
    assert response.covered_window_keys == window_keys


def test_non_axiell_adapter_type_is_rejected() -> None:
    with pytest.raises(ValueError, match="reconcile is Axiell-only"):
        build_runtime("folio")
