import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.extractors.ebsco.config as adapter_config
from adapters.steps.ebsco.loader import EBSCO_NAMESPACE
from adapters.steps.transformer import (
    TransformerEvent,
    TransformerResult,
    build_transformer,
    handler,
)
from tests.adapters.extractors.ebsco.helpers import prepare_changeset
from tests.adapters.transformers.conftest import read_transformer_report
from tests.mocks import MockElasticsearchClient


def _run_transform(
    monkeypatch: pytest.MonkeyPatch,
    *,
    changeset_ids: list[str] | None = None,
    index_date: str | None = None,
    pipeline_date: str = "dev",
) -> TransformerResult:
    monkeypatch.setattr(adapter_config, "PIPELINE_DATE", pipeline_date)
    monkeypatch.setattr(adapter_config, "INDEX_DATE", index_date)

    event = TransformerEvent(
        transformer_type="ebsco",
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
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "ebs00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Parasites, hosts and diseases</subfield></datafield></record>",
    }
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2025-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0
    assert result.job_id == "20250101T1200"
    assert result.changeset_ids == [changeset_id]
    assert (
        result.report_s3_uri
        == f"s3://wellcomecollection-platform-ebsco-adapter/pipeline-dev/ebsco/dev/{changeset_id}__20250101T1200.json"
    )

    report = read_transformer_report(result)
    assert sorted(report["successful_ids"]) == sorted(
        [f"Work[ebsco-alt-lookup/{i}]" for i in records_by_id]
    )

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
        transformer_type="ebsco",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2025-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0
    report = read_transformer_report(result)
    assert sorted(report["successful_ids"]) == sorted(
        [
            "Work[ebsco-alt-lookup/ebs00001]",
            "Work[ebsco-alt-lookup/ebs00003]",
        ]
    )

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    deleted = by_id["Work[ebsco-alt-lookup/ebs00003]"]["_source"]
    assert deleted["type"] == "Deleted"
    assert deleted["deletedReason"]["type"] == "DeletedFromSource"
    assert deleted["deletedReason"]["info"] == "Marked as deleted from source"


def test_build_transformer_uses_provided_snapshot_id_for_reads(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    initial_records = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Snapshot One Title</subfield></datafield></record>"
    }
    initial_changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        initial_records,
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )
    initial_snapshot = temporary_table.current_snapshot()
    assert initial_snapshot is not None
    initial_snapshot_id = initial_snapshot.snapshot_id

    updated_records = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Snapshot Two Title</subfield></datafield></record>"
    }
    prepare_changeset(
        temporary_table,
        monkeypatch,
        updated_records,
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )

    transformer = build_transformer(
        TransformerEvent(
            transformer_type="ebsco",
            job_id="20250101T1200",
            changeset_ids=[initial_changeset_id],
            snapshot_id=initial_snapshot_id,
        ),
        use_rest_api_table=False,
    )

    rows = list(transformer.source.stream_raw())
    assert len(rows) == 1
    assert "Snapshot One Title" in rows[0]["content"]
    assert "Snapshot Two Title" not in rows[0]["content"]


def test_build_transformer_uses_latest_snapshot_when_event_has_no_snapshot_id(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    initial_records = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Initial Snapshot Title</subfield></datafield></record>"
    }
    initial_changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        initial_records,
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )

    transformer = build_transformer(
        TransformerEvent(
            transformer_type="ebsco",
            job_id="20250101T1200",
            changeset_ids=[initial_changeset_id],
        ),
        use_rest_api_table=False,
    )

    updated_records = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Later Snapshot Title</subfield></datafield></record>"
    }
    prepare_changeset(
        temporary_table,
        monkeypatch,
        updated_records,
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )

    rows = list(transformer.source.stream_raw())
    assert transformer.source.snapshot_id is not None
    assert len(rows) == 1
    assert "Initial Snapshot Title" in rows[0]["content"]
    assert "Later Snapshot Title" not in rows[0]["content"]
