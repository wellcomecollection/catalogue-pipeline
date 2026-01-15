import json

import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.ebsco.config as adapter_config
from adapters.ebsco.steps.loader import EBSCO_NAMESPACE
from adapters.transformers.manifests import TransformerManifest
from adapters.transformers.transformer import TransformerEvent, handler
from tests.mocks import MockElasticsearchClient, MockSmartOpen

from .helpers import prepare_changeset


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


def test_transformer_end_to_end_with_local_table(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "ebs00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Parasites, hosts and diseases</subfield></datafield></record>",
    }
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=EBSCO_NAMESPACE,
        build_adapter_table_path="adapters.ebsco.helpers.build_adapter_table",
    )

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
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=EBSCO_NAMESPACE,
        build_adapter_table_path="adapters.ebsco.helpers.build_adapter_table",
    )

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
