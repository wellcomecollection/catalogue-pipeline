import json
from collections.abc import Mapping
from datetime import datetime

import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.ebsco.config as adapter_config
from adapters.transformers.manifests import TransformerManifest
from adapters.transformers.transformer import TransformerEvent, handler
from adapters.utils.adapter_store import AdapterStore
from tests.mocks import MockElasticsearchClient, MockSmartOpen

from .helpers import data_to_namespaced_table

# --------------------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------------------


def _prepare_changeset(
    temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
    records_by_id: Mapping[str, tuple[str, bool] | str | None],
) -> str:
    """Insert XML records into the temporary Iceberg table.

    Args:
        records_by_id: Mapping of id -> content. Content can be:
            - str: visible record with that XML content
            - (str, True): deleted record with that XML content preserved
            - None: legacy format, treated as error (no content)

    Returns the new changeset_id.
    """
    rows = []
    for rid, data in records_by_id.items():
        if isinstance(data, tuple):
            content: str | None = data[0]
            deleted = data[1]
        else:
            content = data
            deleted = False
        rows.append(
            {
                "id": rid,
                "content": content,
                "deleted": deleted,
                "last_modified": datetime.now(),
            }
        )
    pa_table_initial = data_to_namespaced_table(rows, add_timestamp=True)

    client = AdapterStore(temporary_table)

    store_update = client.incremental_update(pa_table_initial, "ebsco")
    assert store_update is not None
    changeset_id = store_update.changeset_id

    assert changeset_id, "Expected a changeset_id to be returned"

    # Ensure transformer uses our temporary table
    monkeypatch.setattr(
        "adapters.ebsco.helpers.build_adapter_table",
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
        transformer_type="ebsco",
        job_id="20250101T1200",
        changeset_ids=changeset_ids or [],
    )
    return handler(event=event, es_mode="local", use_rest_api_table=False)


# --------------------------------------------------------------------------------------
# Tests
# --------------------------------------------------------------------------------------


def test_transformer_end_to_end_with_local_table(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "ebs00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Parasites, hosts and diseases</subfield></datafield></record>",
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

    # Success lines include sourceIdentifiers and jobId
    assert lines == [
        {
            "sourceIdentifiers": [f"Work[ebsco-alt-lookup/{i}]" for i in records_by_id],
            "jobId": "20250101T1200",
        }
    ]

    titles = {
        op["_source"].get("data", {}).get("title")
        for op in MockElasticsearchClient.inputs
    }
    assert titles == {"How to Avoid Huge Ships", "Parasites, hosts and diseases"}


def test_transformer_end_to_end_includes_deletions(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id: dict[str, tuple[str, bool] | str] = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        # Deleted records now retain content with a deleted flag
        "ebs00003": (
            "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00003</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Deleted Work</subfield></datafield></record>",
            True,
        ),
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
        "Work[ebsco-alt-lookup/ebs00001]",
        "Work[ebsco-alt-lookup/ebs00003]",
    }

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    deleted = by_id["Work[ebsco-alt-lookup/ebs00003]"]["_source"]
    assert deleted["type"] == "Deleted"
    assert deleted["deletedReason"]["type"] == "DeletedFromSource"
    assert deleted["deletedReason"]["info"] == "Marked as deleted from source"
