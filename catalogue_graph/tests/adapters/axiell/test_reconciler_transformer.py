import json
from datetime import UTC, datetime, timedelta

import pyarrow as pa
import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.transformers.manifests import TransformerManifest
from adapters.transformers.transformer import TransformerEvent, handler
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.reconciler_store import ReconcilerStore
from adapters.utils.schemata import (
    ADAPTER_STORE_ARROW_SCHEMA,
    RECONCILER_STORE_ARROW_SCHEMA,
)
from tests.mocks import MockElasticsearchClient, MockSmartOpen


def _marcxml(guid: str) -> str:
    return (
        "<record><leader>00000nam a2200000   4500</leader>"
        f"<controlfield tag='001'>{guid}</controlfield>"
        "<datafield tag='245' ind1='0' ind2='0'>"
        f"<subfield code='a'>Title for {guid}</subfield>"
        "</datafield></record>"
    )


def _rows_to_adapter_arrow(rows: list[dict]) -> pa.Table:
    return pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)


def _rows_to_reconciler_arrow(rows: list[dict]) -> pa.Table:
    return pa.Table.from_pylist(rows, schema=RECONCILER_STORE_ARROW_SCHEMA)


def _run_reconciler(
    monkeypatch: pytest.MonkeyPatch,
    adapter_table: IcebergTable,
    reconciler_table: IcebergTable,
    changeset_ids: list[str],
) -> TransformerManifest:
    monkeypatch.setattr(
        "adapters.transformers.transformer.ADAPTER_TABLE_BUILDER_BY_TYPE",
        {
            "axiell_reconciler": lambda use_rest_api_table,
            create_if_not_exists: adapter_table
        },
    )
    monkeypatch.setattr(
        "adapters.transformers.transformer.axiell_helpers.build_reconciler_table",
        lambda use_rest_api_table, create_if_not_exists: reconciler_table,
    )

    event = TransformerEvent(
        transformer_type="axiell_reconciler",
        job_id="test-job-id",
        changeset_ids=changeset_ids,
    )

    return handler(event=event, es_mode="local", use_rest_api_table=False)


def _read_success_lines(manifest: TransformerManifest) -> list[dict]:
    batch_path_full = (
        f"s3://{manifest.successes.batch_file_location.bucket}/"
        f"{manifest.successes.batch_file_location.key}"
    )
    batch_contents_path = MockSmartOpen.file_lookup[batch_path_full]
    with open(batch_contents_path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def test_reconciler_updates_mappings_inserts_new_keeps_existing_and_emits_deleted(
    temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    adapter_store = AdapterStore(temporary_table, namespace="axiell")
    reconciler_store = ReconcilerStore(reconciler_temporary_table, namespace="axiell")

    # Store baseline data in reconciler store
    baseline_time = datetime.now(UTC) - timedelta(days=1)
    reconciler_store.incremental_update(
        _rows_to_reconciler_arrow(
            [
                {
                    "namespace": "axiell",
                    "id": "collect-1",
                    "guid": "guid-old-1",
                    "changeset": None,
                    "last_modified": baseline_time,
                },
                {
                    "namespace": "axiell",
                    "id": "collect-2",
                    "guid": "guid-stays-2",
                    "changeset": None,
                    "last_modified": baseline_time,
                },
            ]
        )
    )

    # Update data in the adapter store
    update_time = datetime.now(UTC)
    adapter_changeset = adapter_store.incremental_update(
        _rows_to_adapter_arrow(
            [
                {
                    "namespace": "axiell",
                    "id": "collect-1",
                    "content": _marcxml("guid-new-1"),
                    "changeset": None,
                    "last_modified": update_time,
                    "deleted": False,
                },
                {
                    "namespace": "axiell",
                    "id": "collect-3",
                    "content": _marcxml("guid-new-3"),
                    "changeset": None,
                    "last_modified": update_time,
                    "deleted": False,
                },
            ]
        )
    )
    assert adapter_changeset is not None

    # Run reconciler with the changeset ID outputted by the adapter store update
    result = _run_reconciler(
        monkeypatch,
        temporary_table,
        reconciler_temporary_table,
        [adapter_changeset.changeset_id],
    )

    # collect-1 is updated, collect-3 is inserted, and collect-2 remains unchanged
    final_mappings = {
        row["id"]: row["guid"]
        for row in reconciler_store.get_namespace_records().to_pylist()
    }
    assert final_mappings == {
        "collect-1": "guid-new-1",
        "collect-2": "guid-stays-2",
        "collect-3": "guid-new-3",
    }

    # Old GUID corresponding to collect-1 should be transformed as a deleted work
    assert result.successes.count == 1
    assert result.failures is None
    assert [op["_id"] for op in MockElasticsearchClient.inputs] == [
        "Work[axiell-guid/guid-old-1]"
    ]

    # Old GUID corresponding to collect-1 should be included in the manifest
    lines = _read_success_lines(result)
    assert lines == [
        {"sourceIdentifiers": ["Work[axiell-guid/guid-old-1]"], "jobId": "test-job-id"}
    ]


def test_reconciler_creates_new_mappings_without_emitting_deleted_works(
    temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    adapter_store = AdapterStore(temporary_table, namespace="axiell")
    reconciler_store = ReconcilerStore(reconciler_temporary_table, namespace="axiell")

    # Store baseline data in adapter store
    adapter_changeset = adapter_store.incremental_update(
        _rows_to_adapter_arrow(
            [
                {
                    "namespace": "axiell",
                    "id": "collect-100",
                    "content": _marcxml("guid-100"),
                    "changeset": None,
                    "last_modified": datetime.now(UTC),
                    "deleted": False,
                },
                {
                    "namespace": "axiell",
                    "id": "collect-101",
                    "content": _marcxml("guid-101"),
                    "changeset": None,
                    "last_modified": datetime.now(UTC),
                    "deleted": False,
                },
            ]
        )
    )
    assert adapter_changeset is not None

    result = _run_reconciler(
        monkeypatch,
        temporary_table,
        reconciler_temporary_table,
        [adapter_changeset.changeset_id],
    )

    # Both works are now stored in the reconciler table
    final_rows = {
        row["id"]: row["guid"]
        for row in reconciler_store.get_namespace_records().to_pylist()
    }
    assert final_rows == {"collect-100": "guid-100", "collect-101": "guid-101"}

    # No deleted works transformed
    assert result.successes.count == 0
    assert result.failures is None
    assert MockElasticsearchClient.inputs == []
    assert _read_success_lines(result) == []


def test_reconciler_does_not_update_or_emit_when_adapter_timestamp_is_older(
    temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    adapter_store = AdapterStore(temporary_table, namespace="axiell")
    reconciler_store = ReconcilerStore(reconciler_temporary_table, namespace="axiell")

    reconciler_store.incremental_update(
        _rows_to_reconciler_arrow(
            [
                {
                    "namespace": "axiell",
                    "id": "collect-older",
                    "guid": "guid-current",
                    "changeset": None,
                    "last_modified": datetime.now(UTC),
                }
            ]
        )
    )

    adapter_changeset = adapter_store.incremental_update(
        _rows_to_adapter_arrow(
            [
                {
                    "namespace": "axiell",
                    "id": "collect-older",
                    "content": _marcxml("guid-older-update-attempt"),
                    "changeset": None,
                    "last_modified": datetime.now(UTC) - timedelta(days=2),
                    "deleted": False,
                }
            ]
        )
    )
    assert adapter_changeset is not None

    result = _run_reconciler(
        monkeypatch,
        temporary_table,
        reconciler_temporary_table,
        [adapter_changeset.changeset_id],
    )

    final_rows = reconciler_store.get_namespace_records().to_pylist()
    assert len(final_rows) == 1
    assert final_rows[0]["id"] == "collect-older"
    assert final_rows[0]["guid"] == "guid-current"

    assert result.successes.count == 0
    assert result.failures is None
    assert MockElasticsearchClient.inputs == []


def test_reconciler_skips_missing_or_invalid_content(
    temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    adapter_store = AdapterStore(temporary_table, namespace="axiell")
    reconciler_store = ReconcilerStore(reconciler_temporary_table, namespace="axiell")

    adapter_changeset = adapter_store.incremental_update(
        _rows_to_adapter_arrow(
            [
                {
                    "namespace": "axiell",
                    "id": "collect-missing-content",
                    "content": None,
                    "changeset": None,
                    "last_modified": datetime.now(UTC),
                    "deleted": False,
                },
                {
                    "namespace": "axiell",
                    "id": "collect-invalid-xml",
                    "content": "<record><controlfield tag='001'>broken",
                    "changeset": None,
                    "last_modified": datetime.now(UTC),
                    "deleted": False,
                },
            ]
        )
    )
    assert adapter_changeset is not None

    result = _run_reconciler(
        monkeypatch,
        temporary_table,
        reconciler_temporary_table,
        [adapter_changeset.changeset_id],
    )

    assert reconciler_store.get_namespace_records().num_rows == 0

    assert result.successes.count == 0
    assert result.failures is not None
    assert result.failures.count == 2
    assert MockElasticsearchClient.inputs == []
