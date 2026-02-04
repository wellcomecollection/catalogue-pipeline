import json

import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.axiell.config as adapter_config
from adapters.axiell.runtime import AXIELL_CONFIG
from adapters.transformers.manifests import TransformerManifest
from adapters.transformers.transformer import TransformerEvent, handler
from tests.adapters.ebsco.helpers import prepare_changeset
from tests.mocks import MockElasticsearchClient, MockSmartOpen

AXIELL_NAMESPACE = AXIELL_CONFIG.config.adapter_namespace


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

    return handler(
        event=event,
        es_mode="local",
        use_rest_api_table=False,
    )


def test_transformer_end_to_end_with_local_table(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ax00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Axiell Title One</subfield></datafield></record>",
        "ax00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Axiell Title Two</subfield></datafield></record>",
    }
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=AXIELL_NAMESPACE,
        build_adapter_table_path="adapters.axiell.runtime.AXIELL_CONFIG.build_adapter_table",
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

    assert lines == [
        {
            "sourceIdentifiers": [f"Work[axiell-guid/{i}]" for i in records_by_id],
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
    records_by_id: dict[str, tuple[str, bool] | str] = {
        "ax00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Axiell Title One</subfield></datafield></record>",
        # Deleted records now retain content with a deleted flag
        "ax00003": (
            "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00003</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Deleted Axiell Work</subfield></datafield></record>",
            True,
        ),
    }
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=AXIELL_NAMESPACE,
        build_adapter_table_path="adapters.axiell.runtime.AXIELL_CONFIG.build_adapter_table",
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
        "Work[axiell-guid/ax00001]",
        "Work[axiell-guid/ax00003]",
    }

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    deleted = by_id["Work[axiell-guid/ax00003]"]["_source"]
    assert deleted["type"] == "Deleted"
    assert deleted["deletedReason"]["type"] == "DeletedFromSource"
    assert deleted["deletedReason"]["info"] == "Marked as deleted from source"
