import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.extractors.oai_pmh.axiell.config as adapter_config
from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG
from adapters.steps.transformer import TransformerEvent, TransformerResult, handler
from tests.adapters.extractors.ebsco.helpers import prepare_changeset
from tests.adapters.transformers.conftest import read_transformer_report
from tests.mocks import MockElasticsearchClient

AXIELL_NAMESPACE = AXIELL_CONFIG.config.adapter_namespace


TEST_RECORD_ONE = "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Axiell Title One</subfield></datafield><datafield tag='035'><subfield code='a'>(Calm RefNo)A/B</subfield></datafield><datafield tag='351'><subfield code='c'>item</subfield></datafield><datafield tag='583' ind1='0'><subfield code='l'>catalogued</subfield></datafield></record>"
TEST_RECORD_TWO = "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Axiell Title Two</subfield></datafield><datafield tag='035'><subfield code='a'>(Calm RefNo)A/B/C</subfield></datafield><datafield tag='351'><subfield code='c'>item</subfield></datafield><datafield tag='583' ind1='0'><subfield code='l'>catalogued</subfield></datafield></record>"


def _run_transform(
    monkeypatch: pytest.MonkeyPatch,
    *,
    changeset_ids: list[str] | None = None,
    ids: list[str] | None = None,
    job_id: str = "20250101T1200",
    index_date: str | None = None,
    pipeline_date: str = "dev",
    facts_table: IcebergTable | None = None,
    reconciler_table: IcebergTable | None = None,
) -> TransformerResult:
    monkeypatch.setattr(adapter_config, "PIPELINE_DATE", pipeline_date)
    monkeypatch.setattr(adapter_config, "INDEX_DATE", index_date)
    if facts_table is not None:
        monkeypatch.setattr(
            "adapters.steps.transformer.AXIELL_CONFIG.build_deletion_facts_table",
            lambda **kwargs: facts_table,
        )
    if reconciler_table is not None:
        monkeypatch.setattr(
            "adapters.steps.transformer.AXIELL_CONFIG.build_reconciler_table",
            lambda **kwargs: reconciler_table,
        )

    event = TransformerEvent(
        transformer_type="axiell",
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
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    records_by_id = {
        "ax00001": TEST_RECORD_ONE,
        "ax00002": TEST_RECORD_TWO,
    }
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2025-01-01",
        facts_table=deletion_facts_temporary_table,
        reconciler_table=reconciler_temporary_table,
    )

    assert result.success_count == 2
    assert result.failure_count == 0
    assert result.job_id == "20250101T1200"
    assert result.changeset_ids == [changeset_id]
    assert (
        result.report_s3_uri
        == f"s3://wellcomecollection-platform-axiell-adapter/pipeline-dev/axiell/dev/{changeset_id}__20250101T1200.json"
    )

    report = read_transformer_report(result)
    assert sorted(report["successful_ids"]) == sorted(
        [f"Work[axiell-guid/{i}]" for i in records_by_id]
    )

    titles = {
        op["_source"].get("data", {}).get("title")
        for op in MockElasticsearchClient.inputs
    }
    assert titles == {"Axiell Title One", "Axiell Title Two"}


def test_transformer_end_to_end_includes_deletions(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    records_by_id: dict[str, tuple[str, bool] | str] = {
        "ax00001": TEST_RECORD_ONE,
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
        transformer_type="axiell",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2025-01-01",
        facts_table=deletion_facts_temporary_table,
        reconciler_table=reconciler_temporary_table,
    )

    report = read_transformer_report(result)
    assert sorted(report["successful_ids"]) == sorted(
        ["Work[axiell-guid/ax00001]", "Work[axiell-guid/ax00003]"]
    )
    assert result.success_count == 2
    assert result.failure_count == 0

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    deleted = by_id["Work[axiell-guid/ax00003]"]["_source"]
    assert deleted["type"] == "Deleted"
    assert deleted["deletedReason"]["type"] == "DeletedFromSource"
    assert deleted["deletedReason"]["info"] == "Marked as deleted from source"


def test_transformer_id_run_transforms_only_named_ids(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    not_requested = "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00003</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Not requested</subfield></datafield><datafield tag='035'><subfield code='a'>(Calm RefNo)A/B/D</subfield></datafield><datafield tag='351'><subfield code='c'>item</subfield></datafield><datafield tag='583' ind1='0'><subfield code='l'>catalogued</subfield></datafield></record>"
    records_by_id = {
        "ax00001": TEST_RECORD_ONE,
        "ax00002": TEST_RECORD_TWO,
        "ax00003": not_requested,
    }
    # The changeset id is irrelevant to an id run; only seed the store.
    prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        ids=["ax00001", "ax00002"],
        index_date="2025-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0
    assert result.changeset_ids == []
    assert result.ids == ["ax00001", "ax00002"]

    indexed_ids = {op["_id"] for op in MockElasticsearchClient.inputs}
    assert indexed_ids == {
        "Work[axiell-guid/ax00001]",
        "Work[axiell-guid/ax00002]",
    }


def test_transformer_id_run_includes_deleted_rows_as_tombstones(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id: dict[str, tuple[str, bool] | str] = {
        "ax00001": TEST_RECORD_ONE,
        "ax00003": (
            "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20251225123045.0</controlfield><controlfield tag='001'>ax00003</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Deleted Axiell Work</subfield></datafield></record>",
            True,
        ),
    }
    prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        ids=["ax00001", "ax00003"],
        index_date="2025-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    deleted = by_id["Work[axiell-guid/ax00003]"]["_source"]
    assert deleted["type"] == "Deleted"
    assert deleted["deletedReason"]["type"] == "DeletedFromSource"


def test_transformer_id_run_report_key_is_not_reindex(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ax00001": TEST_RECORD_ONE,
    }
    prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )

    result = _run_transform(
        monkeypatch,
        ids=["ax00001"],
        index_date="2025-01-01",
        job_id="idload-20250101T1200",
    )

    assert (
        result.report_s3_uri
        == "s3://wellcomecollection-platform-axiell-adapter/pipeline-dev/axiell/dev/idload__idload-20250101T1200.json"
    )

    report = read_transformer_report(result)
    assert report["ids"] == ["ax00001"]
    assert report["changeset_ids"] == []
