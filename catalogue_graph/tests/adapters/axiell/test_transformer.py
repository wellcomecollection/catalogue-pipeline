import json
from collections.abc import Mapping
from datetime import datetime

import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.axiell.config as adapter_config
from adapters.axiell.steps.loader import AXIELL_NAMESPACE
from adapters.transformers.manifests import TransformerManifest
from adapters.transformers.transformer import TransformerEvent, handler
from adapters.utils.adapter_store import AdapterStore
from tests.adapters.ebsco.helpers import data_to_namespaced_table
from tests.mocks import MockElasticsearchClient, MockSmartOpen


def _prepare_changeset(
    temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
    records_by_id: Mapping[str, str | None],
) -> str:
    """Insert XML records (mapping of id -> MARC XML) into the temporary Iceberg table.

    Returns the new changeset_id.
    """
    rows = [
        {"id": rid, "content": data, "last_modified": datetime.now()}
        for rid, data in records_by_id.items()
    ]
    pa_table_initial = data_to_namespaced_table(
        rows, namespace=AXIELL_NAMESPACE, add_timestamp=True
    )

    client = AdapterStore(temporary_table)

    store_update = client.incremental_update(pa_table_initial, AXIELL_NAMESPACE)
    assert store_update is not None
    changeset_id = store_update.changeset_id

    assert changeset_id, "Expected a changeset_id to be returned"

    # Ensure transformer uses our temporary table
    monkeypatch.setattr(
        "adapters.axiell.helpers.build_adapter_table",
        lambda use_rest_api_table, create_if_not_exists: temporary_table,
    )
    return changeset_id


def _run_transform(
    monkeypatch: pytest.MonkeyPatch,
    *,
    changeset_ids: list[str] | None = None,
    index_date: str | None = None,
    pipeline_date: str = "dev",
) -> TransformerManifest:
    monkeypatch.setattr(adapter_config, "PIPELINE_DATE", pipeline_date)
    monkeypatch.setattr(adapter_config, "INDEX_DATE", index_date)

    event = TransformerEvent(
        transformer_type="axiell",
        job_id="20250101T1200",
        changeset_ids=changeset_ids or [],
    )
    return handler(event=event, es_mode="local", use_rest_api_table=False)


def test_transformer_end_to_end_with_local_table(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ax00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Axiell Title One</subfield></datafield></record>",
        "ax00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Axiell Title Two</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2025-01-01",
    )

    assert result.successes.count == 2
    assert result.failures is None
    assert result.job_id == "20250101T1200"
    assert result.changeset_ids == [changeset_id]

    # Validate file contents written to mock S3 (NDJSON)
    batch_path_full = f"s3://{result.successes.batch_file_location.bucket}/{result.successes.batch_file_location.key}"
    batch_contents_path = MockSmartOpen.file_lookup[batch_path_full]
    with open(batch_contents_path, encoding="utf-8") as f:
        lines = [json.loads(line) for line in f if line.strip()]

    assert lines == [
        {
            "sourceIdentifiers": [f"Work[axiell-priref/{i}]" for i in records_by_id],
            "jobId": "20250101T1200",
        }
    ]

    titles = {
        op["_source"].get("data", {}).get("title")
        for op in MockElasticsearchClient.inputs
    }
    assert titles == {"Axiell Title One", "Axiell Title Two"}


def test_transformer_end_to_end_includes_deletions(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id: dict[str, str | None] = {
        "ax00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Axiell Title One</subfield></datafield></record>",
        # Deleted records are represented by empty/None content in the adapter store.
        "ax00003": None,
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2025-01-01",
    )

    assert result.successes.count == 2
    assert result.failures is None

    batch_path_full = f"s3://{result.successes.batch_file_location.bucket}/{result.successes.batch_file_location.key}"
    batch_contents_path = MockSmartOpen.file_lookup[batch_path_full]
    with open(batch_contents_path, encoding="utf-8") as f:
        lines = [json.loads(line) for line in f if line.strip()]

    assert len(lines) == 1
    assert set(lines[0]["sourceIdentifiers"]) == {
        "Work[axiell-priref/ax00001]",
        "Work[axiell-priref/ax00003]",
    }

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    deleted = by_id["Work[axiell-priref/ax00003]"]["_source"]
    assert deleted["type"] == "Deleted"
    assert deleted["deletedReason"]["type"] == "DeletedFromSource"
    assert deleted["deletedReason"]["info"] == "Marked as deleted from source"
