import json

import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.extractors.oai_pmh.folio.config as adapter_config
from adapters.extractors.oai_pmh.folio.runtime import FOLIO_CONFIG
from adapters.steps.transformer import TransformerEvent, handler
from adapters.transformers.manifests import TransformerManifest
from tests.adapters.extractors.ebsco.helpers import prepare_changeset
from tests.mocks import MockElasticsearchClient, MockSmartOpen

FOLIO_NAMESPACE = FOLIO_CONFIG.config.adapter_namespace


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
        transformer_type="folio",
        job_id="20260101T1200",
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
        "fo00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Title One</subfield></datafield></record>",
        "fo00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Title Two</subfield></datafield></record>",
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

    assert result.successes.count == 2
    assert result.failures is None
    assert result.job_id == "20260101T1200"
    assert result.changeset_ids == [changeset_id]

    # Validate file contents written to mock S3 (NDJSON)
    batch_path_full = f"s3://{result.successes.batch_file_location.bucket}/{result.successes.batch_file_location.key}"
    batch_contents_path = MockSmartOpen.file_lookup[batch_path_full]
    with open(batch_contents_path, encoding="utf-8") as f:
        lines = [json.loads(line) for line in f if line.strip()]

    assert lines == [
        {
            "sourceIdentifiers": [f"Work[folio-instance/{i}]" for i in records_by_id],
            "jobId": "20260101T1200",
        }
    ]


def test_transformer_end_to_end_includes_deletions(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id: dict[str, tuple[str, bool] | str] = {
        "fo00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio Title One</subfield></datafield></record>",
        "fo00003": (
            "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00003</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Deleted Folio Work</subfield></datafield></record>",
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

    assert result.successes.count == 2
    assert result.failures is None

    batch_path_full = f"s3://{result.successes.batch_file_location.bucket}/{result.successes.batch_file_location.key}"
    batch_contents_path = MockSmartOpen.file_lookup[batch_path_full]
    with open(batch_contents_path, encoding="utf-8") as f:
        lines = [json.loads(line) for line in f if line.strip()]

    assert len(lines) == 1
    assert set(lines[0]["sourceIdentifiers"]) == {
        "Work[folio-instance/fo00001]",
        "Work[folio-instance/fo00003]",
    }

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    deleted = by_id["Work[folio-instance/fo00003]"]["_source"]
    assert deleted["type"] == "Deleted"
    assert deleted["deletedReason"]["type"] == "DeletedFromSource"
    assert deleted["deletedReason"]["info"] == "Marked as deleted from source"


def test_transformer_includes_suppressions(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Test that records marked with FOLIO suppression marker ($t=1 in MARC 999) are treated as deleted."""
    records_by_id = {
        "fo00005": '<record xmlns:marc="http://www.loc.gov/MARC21/slim"><marc:leader>00422nam a2200109Ia 4500</marc:leader><marc:controlfield tag="001">fo00005</marc:controlfield><marc:controlfield tag="005">20260610153507.9</marc:controlfield><marc:datafield tag="245" ind1="1" ind2="0"><marc:subfield code="a">Visible Folio Work</marc:subfield></marc:datafield></record>',
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

    assert result.successes.count == 2
    assert result.failures is None

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}

    # Visible record should be transformed normally
    visible = by_id["Work[folio-instance/fo00005]"]["_source"]
    assert visible["type"] == "Visible"

    # Suppressed record should be treated as deleted with SuppressedFromSource reason
    suppressed = by_id["Work[folio-instance/fo00006]"]["_source"]
    assert suppressed["type"] == "Deleted"
    assert suppressed["deletedReason"]["type"] == "SuppressedFromSource"
    assert suppressed["deletedReason"]["info"] == "Folio"


def test_transformer_includes_predecessor_identifier(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "fo00004": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='005'>20261225123045.0</controlfield><controlfield tag='001'>fo00004</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Folio With Predecessor</subfield></datafield><datafield tag='907' ind1=' ' ind2=' '><subfield code='a'>b12345679</subfield></datafield></record>",
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

    assert result.successes.count == 1
    assert result.failures is None

    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    source = by_id["Work[folio-instance/fo00004]"]["_source"]
    assert source["type"] == "Visible"
    assert source["state"]["predecessorIdentifier"] == {
        "identifierType": {"id": "sierra-system-number"},
        "ontologyType": "Work",
        "value": "b12345679",
    }
