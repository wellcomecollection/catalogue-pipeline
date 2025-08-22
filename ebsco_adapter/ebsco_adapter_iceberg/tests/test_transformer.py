from typing import cast  # added for dummy ES client

import pyarrow as pa
import pytest
from elasticsearch import Elasticsearch  # added

from models.work import TransformedWork  # type: ignore[import-not-found]
from steps.transformer import (
    EbscoAdapterTransformerConfig,
    EbscoAdapterTransformerEvent,
    EbscoAdapterTransformerResult,
    handler,
    load_data,
    transform,
)
from utils.iceberg import IcebergTableClient

from .helpers import data_to_namespaced_table
from .test_mocks import MockElasticsearchClient, MockSecretsManagerClient

# --------------------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------------------


def _prepare_changeset(
    temporary_table: pa.Table,
    monkeypatch: pytest.MonkeyPatch,
    xml_records: list[str],
) -> str:  # added return type
    """Insert XML records into the temporary Iceberg table and return the new changeset_id."""
    pa_table_initial = data_to_namespaced_table(
        [{"id": "batch", "content": record} for record in xml_records]
    )
    client = IcebergTableClient(temporary_table)
    changeset_id = client.update(pa_table_initial, "ebsco")
    assert changeset_id, "Expected a changeset_id to be returned"

    # Ensure transformer uses our temporary table
    monkeypatch.setattr(
        "steps.transformer.get_local_table", lambda **kwargs: temporary_table
    )
    return changeset_id


def _run_transform(
    changeset_id: str, index_date: str | None, pipeline_date: str = "dev"
) -> EbscoAdapterTransformerResult:
    event = EbscoAdapterTransformerEvent(
        changeset_id=changeset_id, index_date=index_date
    )
    config = EbscoAdapterTransformerConfig(
        is_local=True, use_glue_table=False, pipeline_date=pipeline_date
    )
    return handler(event=event, config_obj=config)


def _add_pipeline_secrets(pipeline_date: str) -> None:
    """Provide the minimal set of ES secrets required for get_client for a pipeline_date."""
    secret_prefix = f"elasticsearch/pipeline_storage_{pipeline_date}"
    for name, value in [
        ("public_host", "test"),
        ("private_host", "test"),
        ("port", 80),
        ("protocol", "http"),
        ("ebsco_adapter_ideberg/api_key", ""),
        ("ebsco_adapter_iceberg/api_key", ""),  # variant used in some code paths
    ]:
        MockSecretsManagerClient.add_mock_secret(f"{secret_prefix}/{name}", value)


# --------------------------------------------------------------------------------------
# Tests (existing end-to-end & integration)
# --------------------------------------------------------------------------------------


def test_transformer_end_to_end_with_local_table(
    temporary_table: pa.Table, monkeypatch: pytest.MonkeyPatch
) -> None:
    xml_records = [
        "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Parasites, hosts and diseases</subfield></datafield></record>",
    ]
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, xml_records)

    result = _run_transform(changeset_id, index_date="2025-01-01")

    assert result.records_transformed == 2
    titles = {op["_source"]["title"] for op in MockElasticsearchClient.inputs}
    assert titles == {"How to Avoid Huge Ships", "Parasites, hosts and diseases"}


def test_transformer_no_changeset_returns_zero(monkeypatch: pytest.MonkeyPatch) -> None:
    event = EbscoAdapterTransformerEvent(changeset_id=None)
    config = EbscoAdapterTransformerConfig(
        is_local=True, use_glue_table=False, pipeline_date="dev"
    )

    result = handler(event=event, config_obj=config)
    assert result.records_transformed == 0
    assert MockElasticsearchClient.inputs == []


@pytest.mark.parametrize(
    "index_date, pipeline_date, expected_index",
    [
        ("2025-02-02", "dev", "concepts-indexed-2025-02-02"),  # explicit index_date
        (None, "dev", "concepts-indexed-dev"),  # falls back to pipeline_date default
        (
            None,
            "2025-05-05",
            "concepts-indexed-2025-05-05",
        ),  # custom pipeline_date when no index_date
    ],
)
def test_transformer_index_name_selection(
    temporary_table: pa.Table,
    monkeypatch: pytest.MonkeyPatch,
    index_date: str | None,
    pipeline_date: str,
    expected_index: str,
) -> None:
    """The ES index name should use index_date if provided; otherwise pipeline_date (custom or default)."""
    # Provide required secrets for any pipeline_date used in this parameterised test
    _add_pipeline_secrets(pipeline_date)

    xml_records = [
        "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsIdx001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Some Title</subfield></datafield></record>",
    ]
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, xml_records)

    _run_transform(changeset_id, index_date=index_date, pipeline_date=pipeline_date)

    indices = {op["_index"] for op in MockElasticsearchClient.inputs}
    assert indices == {expected_index}


# --------------------------------------------------------------------------------------
# Tests for transform()
# --------------------------------------------------------------------------------------


def test_transform_empty_content_returns_empty_list() -> None:
    assert transform("work1", "") == []


def test_transform_invalid_xml_returns_empty_list() -> None:
    # malformed XML so pymarc should throw and transform should swallow
    assert transform("work2", "<record><leader>bad") == []


def test_transform_valid_marcxml_returns_work() -> None:
    xml = (
        "<record>"
        "<leader>00000nam a2200000   4500</leader>"
        "<controlfield tag='001'>ebs12345</controlfield>"
        "<datafield tag='245' ind1='0' ind2='0'>"
        "<subfield code='a'>A Useful Title</subfield>"
        "</datafield>"
        "</record>"
    )
    result = transform("ebs12345", xml)
    assert len(result) == 1
    assert result[0].id == "ebs12345"
    assert result[0].title == "A Useful Title"


# --------------------------------------------------------------------------------------
# Tests for load_data()
# --------------------------------------------------------------------------------------


def test_load_data_success_no_errors(monkeypatch: pytest.MonkeyPatch) -> None:
    collected: list[dict] = []

    def fake_bulk(client, actions, raise_on_error, stats_only):  # type: ignore[no-untyped-def]
        for a in actions:
            collected.append(a)
        return len(collected), []  # success count, no errors

    monkeypatch.setattr("elasticsearch.helpers.bulk", fake_bulk)

    records = [
        TransformedWork(id="id1", title="Title 1"),
        TransformedWork(id="id2", title="Title 2"),
    ]
    dummy_client = cast(Elasticsearch, object())
    success, errors = load_data(
        elastic_client=dummy_client, records=records, index_name="concepts-indexed-dev"
    )

    assert success == 2
    assert errors == 0
    assert {a["_id"] for a in collected} == {"id1", "id2"}
    assert {a["_source"]["title"] for a in collected} == {"Title 1", "Title 2"}


def test_load_data_with_errors(monkeypatch: pytest.MonkeyPatch) -> None:
    def fake_bulk(client, actions, raise_on_error, stats_only):  # type: ignore[no-untyped-def]
        actions_list = list(actions)
        return 1, [
            {
                "index": {
                    "_id": actions_list[0]["_id"],
                    "status": 400,
                    "error": {"type": "mapper_parsing_exception"},
                }
            }
        ]

    monkeypatch.setattr("elasticsearch.helpers.bulk", fake_bulk)

    records = [TransformedWork(id="id1", title="Bad Title")]
    dummy_client = cast(Elasticsearch, object())
    success, errors = load_data(
        elastic_client=dummy_client, records=records, index_name="concepts-indexed-dev"
    )

    assert success == 1
    assert errors == 1
