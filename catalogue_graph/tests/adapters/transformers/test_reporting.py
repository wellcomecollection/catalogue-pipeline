import json

import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.extractors.oai_pmh.folio.config as adapter_config
from adapters.extractors.oai_pmh.folio.runtime import FOLIO_CONFIG
from adapters.steps.transformer import TransformerEvent, handler
from tests.adapters.extractors.ebsco.helpers import prepare_changeset
from tests.adapters.transformers.folio.helpers import make_items_store
from tests.mocks import MockCloudwatchClient, MockSmartOpen

FOLIO_NAMESPACE = FOLIO_CONFIG.config.adapter_namespace

_MINIMAL_RECORD = (
    "<record>"
    "<leader>00000nam a2200000   4500</leader>"
    "<controlfield tag='005'>20261225123045.0</controlfield>"
    "<controlfield tag='001'>{id}</controlfield>"
    "<datafield tag='245' ind1='0' ind2='0'>"
    "<subfield code='a'>Title {id}</subfield>"
    "</datafield>"
    "<datafield tag='999' ind1='f' ind2='f'>"
    "<subfield code='i'>{id}</subfield>"
    "</datafield>"
    "</record>"
)


def _run_transform(
    monkeypatch: pytest.MonkeyPatch,
    *,
    changeset_ids: list[str],
    pipeline_date: str = "2026-01-01",
) -> None:
    monkeypatch.setattr(adapter_config, "PIPELINE_DATE", pipeline_date)
    monkeypatch.setattr(adapter_config, "INDEX_DATE", None)

    items_store = make_items_store({})
    monkeypatch.setattr(
        "adapters.steps.transformer.build_items_store",
        lambda **kwargs: items_store,
    )

    event = TransformerEvent(
        transformer_type="folio",
        job_id="20260101T1200",
        changeset_ids=changeset_ids,
    )
    handler(event=event, es_mode="local", use_rest_api_table=False)


def test_transformer_run_publishes_counts_to_cloudwatch(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {
            "fo00001": _MINIMAL_RECORD.format(id="fo00001"),
            "fo00002": _MINIMAL_RECORD.format(id="fo00002"),
            "fo00003": "invalid data",
        },
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    _run_transform(monkeypatch, changeset_ids=[changeset_id])

    reported = {m["metric_name"]: m for m in MockCloudwatchClient.metrics_reported}
    assert reported["success_count"]["value"] == 2
    assert reported["failure_count"]["value"] == 1


def test_transformer_run_cloudwatch_dimensions(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"fo00001": _MINIMAL_RECORD.format(id="fo00001")},
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    _run_transform(
        monkeypatch, changeset_ids=[changeset_id], pipeline_date="2026-03-15"
    )

    for metric in MockCloudwatchClient.metrics_reported:
        assert metric["dimensions"]["pipeline_date"] == "2026-03-15"
        assert metric["dimensions"]["transformer_type"] == "folio"


def test_transformer_run_skips_cloudwatch_for_dev_pipeline_date(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"fo00001": _MINIMAL_RECORD.format(id="fo00001")},
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    _run_transform(monkeypatch, changeset_ids=[changeset_id], pipeline_date="dev")

    assert MockCloudwatchClient.metrics_reported == []


def test_transformer_run_writes_report_to_s3(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"fo00001": _MINIMAL_RECORD.format(id="fo00001")},
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )
    items_store = make_items_store({})
    monkeypatch.setattr(
        "adapters.steps.transformer.build_items_store",
        lambda **kwargs: items_store,
    )
    monkeypatch.setattr(adapter_config, "PIPELINE_DATE", "2026-03-15")
    monkeypatch.setattr(adapter_config, "INDEX_DATE", None)

    event = TransformerEvent(
        transformer_type="folio",
        job_id="20260101T1200",
        changeset_ids=[changeset_id],
    )
    handler(event=event, es_mode="local", use_rest_api_table=False)

    expected_uri = (
        f"s3://{adapter_config.S3_BUCKET}"
        f"/pipeline-2026-03-15/folio/{adapter_config.S3_PREFIX}"
        f"/{changeset_id}__20260101T1200.json"
    )
    assert expected_uri in MockSmartOpen.file_lookup

    with open(MockSmartOpen.file_lookup[expected_uri], encoding="utf-8") as f:
        report = json.loads(f.read())

    assert report["successful_ids"] == ["Work[folio-instance/fo00001]"]
    assert report["errors"] == []
    assert report["changeset_ids"] == [changeset_id]
    assert report["transformer_type"] == "folio"
