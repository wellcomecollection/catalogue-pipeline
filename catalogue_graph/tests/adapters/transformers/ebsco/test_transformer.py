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
from tests.adapters.extractors.ebsco.helpers import lone_element, prepare_changeset
from tests.adapters.transformers.conftest import read_transformer_report
from tests.mocks import MockElasticsearchClient


def _run_transform(
    monkeypatch: pytest.MonkeyPatch,
    *,
    changeset_ids: list[str] | None = None,
    ids: list[str] | None = None,
    index_date: str | None = None,
    pipeline_date: str = "dev",
    job_id: str = "20250101T1200",
) -> TransformerResult:
    monkeypatch.setattr(adapter_config, "PIPELINE_DATE", pipeline_date)
    monkeypatch.setattr(adapter_config, "INDEX_DATE", index_date)

    event = TransformerEvent(
        transformer_type="ebsco",
        job_id=job_id,
        changeset_ids=changeset_ids or [],
        ids=ids,
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


def test_transformer_survives_messy_production_date(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    """
    Real record ebs375800e: its 260$c date ("MDCCLXXXVIII.-MDCCLXXXIX.
    [1788-1789]") used to crash the whole record's transform.
    """
    records_by_id = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "ebs375800e": (
            "<record><leader>00000cas a22000003  4500</leader>"
            "<controlfield tag='001'>ebs375800e</controlfield>"
            "<controlfield tag='008'>970128d17881789enkwr p o ||| 0  |a0eng c</controlfield>"
            "<datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Lounger's miscellany</subfield></datafield>"
            "<datafield tag='260' ind1=' ' ind2=' '>"
            "<subfield code='a'>London [England] :</subfield>"
            "<subfield code='c'>MDCCLXXXVIII.-MDCCLXXXIX. [1788-1789]</subfield>"
            "</datafield>"
            "</record>"
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

    by_id = {op["_id"]: op["_source"] for op in MockElasticsearchClient.inputs}
    production = lone_element(
        by_id["Work[ebsco-alt-lookup/ebs375800e]"]["data"]["production"]
    )
    period = lone_element(production["dates"])
    assert period["range"]["from"] == "1788-01-01T00:00:00Z"
    assert period["range"]["to"] == "1789-12-31T23:59:59.999999999Z"


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


def test_transformer_id_run_transforms_only_named_ids(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "ebs00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Parasites, hosts and diseases</subfield></datafield></record>",
        "ebs00003": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00003</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Not requested</subfield></datafield></record>",
    }
    # The changeset id is irrelevant to an id run; only seed the store.
    prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        ids=["ebs00001", "ebs00002"],
        index_date="2025-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0
    assert result.changeset_ids == []
    assert result.ids == ["ebs00001", "ebs00002"]

    indexed_ids = {op["_id"] for op in MockElasticsearchClient.inputs}
    assert indexed_ids == {
        "Work[ebsco-alt-lookup/ebs00001]",
        "Work[ebsco-alt-lookup/ebs00002]",
    }


def test_transformer_id_run_includes_deleted_rows_as_tombstones(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id: dict[str, tuple[str, bool] | str] = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "ebs00003": (
            "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00003</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Deleted Work</subfield></datafield></record>",
            True,
        ),
    }
    prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        ids=["ebs00001", "ebs00003"],
        index_date="2025-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    deleted = by_id["Work[ebsco-alt-lookup/ebs00003]"]["_source"]
    assert deleted["type"] == "Deleted"
    assert deleted["deletedReason"]["type"] == "DeletedFromSource"


def test_transformer_id_run_reports_unmatched_ids(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
    }
    prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        ids=["ebs00001", "ebs-does-not-exist"],
        index_date="2025-01-01",
    )

    assert result.success_count == 1
    assert result.failure_count == 0
    assert result.unmatched_count == 1

    report = read_transformer_report(result)
    assert report["unmatched_ids"] == ["ebs-does-not-exist"]


def test_transformer_id_run_report_key_is_not_reindex(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
    }
    prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )

    result = _run_transform(
        monkeypatch,
        ids=["ebs00001"],
        index_date="2025-01-01",
        job_id="idload-20250101T1200",
    )

    assert (
        result.report_s3_uri
        == "s3://wellcomecollection-platform-ebsco-adapter/pipeline-dev/ebsco/dev/idload__idload-20250101T1200.json"
    )

    report = read_transformer_report(result)
    assert report["ids"] == ["ebs00001"]
    assert report["changeset_ids"] == []
