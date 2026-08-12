import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.extractors.oai_pmh.folio.config as adapter_config
from adapters.extractors.oai_pmh.folio.runtime import FOLIO_CONFIG
from adapters.steps.transformer import TransformerEvent, TransformerResult, handler
from tests.adapters.extractors.ebsco.helpers import prepare_changeset
from tests.adapters.transformers.conftest import read_transformer_report
from tests.adapters.transformers.folio.helpers import make_items_store
from tests.mocks import MockElasticsearchClient

FOLIO_NAMESPACE = FOLIO_CONFIG.config.adapter_namespace


def _run_transform(
    monkeypatch: pytest.MonkeyPatch,
    *,
    changeset_ids: list[str] | None = None,
    ids: list[str] | None = None,
    job_id: str = "20260101T1200",
    index_date: str | None = None,
    pipeline_date: str = "dev",
) -> TransformerResult:
    monkeypatch.setattr(adapter_config, "PIPELINE_DATE", pipeline_date)
    monkeypatch.setattr(adapter_config, "INDEX_DATE", index_date)

    # The transformer requires the items store to exist (a missing table fails the
    # transform), so provide an empty one rather than relying on local catalog state.
    items_store = make_items_store({})
    monkeypatch.setattr(
        "adapters.steps.transformer.build_items_store",
        lambda **kwargs: items_store,
    )

    event = TransformerEvent(
        transformer_type="folio",
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
        "fo00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Title One</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000001</subfield></datafield></record>",
        "fo00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Title Two</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000002</subfield></datafield></record>",
    }
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2026-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0
    assert result.job_id == "20260101T1200"
    assert result.changeset_ids == [changeset_id]
    assert (
        result.report_s3_uri
        == f"s3://wellcomecollection-platform-folio-adapter/pipeline-dev/folio/dev/{changeset_id}__20260101T1200.json"
    )

    report = read_transformer_report(result)
    assert sorted(report["successful_ids"]) == sorted(
        [
            "Work[folio-instance/10000000-0000-0000-0000-000000000001]",
            "Work[folio-instance/10000000-0000-0000-0000-000000000002]",
        ]
    )


def test_transformer_end_to_end_includes_deletions(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id: dict[str, tuple[str, bool] | str] = {
        "fo00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Title One</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000001</subfield></datafield></record>",
        "fo00003": (
            "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00003</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Deleted Folio Work</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000003</subfield></datafield></record>",
            True,
        ),
    }
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2026-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0
    report = read_transformer_report(result)
    assert sorted(report["successful_ids"]) == sorted(
        [
            "Work[folio-instance/10000000-0000-0000-0000-000000000001]",
            "Work[folio-instance/10000000-0000-0000-0000-000000000003]",
        ]
    )

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    deleted = by_id["Work[folio-instance/10000000-0000-0000-0000-000000000003]"][
        "_source"
    ]
    assert deleted["type"] == "Deleted"
    assert deleted["deletedReason"]["type"] == "DeletedFromSource"
    assert deleted["deletedReason"]["info"] == "Marked as deleted from source"


def test_transformer_includes_suppressions(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Test that records marked with FOLIO suppression marker ($t=1 in MARC 999) are treated as deleted."""
    records_by_id = {
        "fo00005": '<record xmlns:marc="http://www.loc.gov/MARC21/slim"><marc:leader>00422nam a2200109Ia 4500</marc:leader><marc:controlfield tag="001">fo00005</marc:controlfield><marc:controlfield tag="005">20260610153507.9</marc:controlfield><marc:datafield tag="245" ind1="1" ind2="0"><marc:subfield code="a">Visible Folio Work</marc:subfield></marc:datafield><marc:datafield tag="999" ind1="f" ind2="f"><marc:subfield code="i">10000000-0000-0000-0000-000000000005</marc:subfield></marc:datafield></record>',
        "fo00006": '<record xmlns:marc="http://www.loc.gov/MARC21/slim"><marc:leader>00422nam a2200109Ia 4500</marc:leader><marc:controlfield tag="001">fo00006</marc:controlfield><marc:controlfield tag="005">20260610153507.9</marc:controlfield><marc:datafield tag="245" ind1="1" ind2="0"><marc:subfield code="a">Suppressed Folio Work</marc:subfield></marc:datafield><marc:datafield tag="999" ind1="f" ind2="f"><marc:subfield code="i">73822760-c6e3-4be4-a644-fe97fb32567f</marc:subfield><marc:subfield code="t">1</marc:subfield></marc:datafield></record>',
    }
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2026-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}

    # Visible record should be transformed normally
    visible = by_id["Work[folio-instance/10000000-0000-0000-0000-000000000005]"][
        "_source"
    ]
    assert visible["type"] == "Visible"

    # Suppressed record should be treated as deleted with SuppressedFromSource reason
    suppressed = by_id["Work[folio-instance/73822760-c6e3-4be4-a644-fe97fb32567f]"][
        "_source"
    ]
    assert suppressed["type"] == "Deleted"
    assert suppressed["deletedReason"]["type"] == "SuppressedFromSource"
    assert suppressed["deletedReason"]["info"] == "Folio"


def test_transformer_includes_predecessor_identifier(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "fo00004": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00004</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio With Predecessor</subfield></datafield><datafield tag='907' ind1=' ' ind2=' '><subfield code='a'>b12345679</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000004</subfield></datafield></record>",
    }
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2026-01-01",
    )

    assert result.success_count == 1
    assert result.failure_count == 0

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    source = by_id["Work[folio-instance/10000000-0000-0000-0000-000000000004]"][
        "_source"
    ]
    assert source["type"] == "Visible"
    assert source["state"]["predecessorIdentifier"] == {
        "identifierType": {"id": "sierra-system-number"},
        "ontologyType": "Work",
        "value": "b12345679",
    }


def test_transformer_id_run_transforms_only_named_ids(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "fo00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Title One</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000001</subfield></datafield></record>",
        "fo00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Title Two</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000002</subfield></datafield></record>",
        "fo00003": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00003</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Not requested</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000003</subfield></datafield></record>",
    }
    # The changeset id is irrelevant to an id run; only seed the store.
    prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        ids=["fo00001", "fo00002"],
        index_date="2026-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0
    assert result.changeset_ids == []
    assert result.ids == ["fo00001", "fo00002"]

    indexed_ids = {op["_id"] for op in MockElasticsearchClient.inputs}
    assert indexed_ids == {
        "Work[folio-instance/10000000-0000-0000-0000-000000000001]",
        "Work[folio-instance/10000000-0000-0000-0000-000000000002]",
    }


def test_transformer_id_run_includes_deleted_rows_as_tombstones(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id: dict[str, tuple[str, bool] | str] = {
        "fo00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Title One</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000001</subfield></datafield></record>",
        "fo00003": (
            "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00003</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Deleted Folio Work</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000003</subfield></datafield></record>",
            True,
        ),
    }
    prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        ids=["fo00001", "fo00003"],
        index_date="2026-01-01",
    )

    assert result.success_count == 2
    assert result.failure_count == 0

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    deleted = by_id["Work[folio-instance/10000000-0000-0000-0000-000000000003]"][
        "_source"
    ]
    assert deleted["type"] == "Deleted"
    assert deleted["deletedReason"]["type"] == "DeletedFromSource"


def test_transformer_id_run_report_key_is_not_reindex(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "fo00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Title One</subfield></datafield><datafield tag='999' ind1='f' ind2='f'><subfield code='i'>10000000-0000-0000-0000-000000000001</subfield></datafield></record>",
    }
    prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    result = _run_transform(
        monkeypatch,
        ids=["fo00001"],
        index_date="2026-01-01",
        job_id="idload-20260101T1200",
    )

    assert (
        result.report_s3_uri
        == "s3://wellcomecollection-platform-folio-adapter/pipeline-dev/folio/dev/idload__idload-20260101T1200.json"
    )

    report = read_transformer_report(result)
    assert report["ids"] == ["fo00001"]
    assert report["changeset_ids"] == []


def test_transformer_source_identifier_requires_instance_uuid(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    """A FOLIO record without a 999 $i instance UUID must fail loudly rather than fall back to the 001 HRID."""
    records_by_id = {
        "fo00007": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00007</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Without Instance UUID</subfield></datafield></record>",
    }
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        records_by_id,
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    MockElasticsearchClient.inputs.clear()

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2026-01-01",
    )

    assert result.success_count == 0
    assert result.failure_count == 1
