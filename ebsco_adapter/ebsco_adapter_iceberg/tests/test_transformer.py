from contextlib import suppress  # for ruff SIM105 fix
from typing import cast  # added for dummy ES client

import pyarrow as pa
import pytest
from elasticsearch import Elasticsearch  # added

from models.work import SourceWork
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
    records_by_id: dict[str, str],
) -> str:
    """Insert XML records (mapping of id -> MARC XML) into the temporary Iceberg table.

    Returns the new changeset_id.
    """
    rows = [
        {"id": rid, "content": record_xml} for rid, record_xml in records_by_id.items()
    ]
    pa_table_initial = data_to_namespaced_table(rows)
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
        changeset_id=changeset_id,
        index_date=index_date,
        job_id="20250101T1200",
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
        (
            "transformer-ebsco-test/api_key",
            "",
        ),  # another variant observed in get_client
    ]:
        MockSecretsManagerClient.add_mock_secret(f"{secret_prefix}/{name}", value)


# --------------------------------------------------------------------------------------
# Tests (existing end-to-end & integration)
# --------------------------------------------------------------------------------------


def test_transformer_end_to_end_with_local_table(
    temporary_table: pa.Table, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "ebs00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Parasites, hosts and diseases</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    result = _run_transform(changeset_id, index_date="2025-01-01")

    assert result.batches == [list(records_by_id.keys())]
    assert result.success_count == 2
    assert result.failure_count == 0
    titles = {op["_source"].get("title") for op in MockElasticsearchClient.inputs}
    assert titles == {"How to Avoid Huge Ships", "Parasites, hosts and diseases"}


def test_transformer_creates_deletedwork_for_empty_content(
    temporary_table: pa.Table, monkeypatch: pytest.MonkeyPatch
) -> None:
    # Note: empty string content indicates deletion
    records_by_id = {
        "ebsDel001": "",  # deletion marker
        "ebsDel002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsDel002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Alive Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    result = _run_transform(changeset_id, index_date="2025-03-01")

    # Both IDs should appear (one DeletedWork, one SourceWork)
    assert result.batches == [["ebsDel001", "ebsDel002"]]
    assert result.success_count == 2
    deleted_docs = [
        op for op in MockElasticsearchClient.inputs if op["_id"] == "ebsDel001"
    ]
    assert len(deleted_docs) == 1
    deleted_source = deleted_docs[0]["_source"]
    assert deleted_source["deletedReason"] == "not found in EBSCO source"
    # Ensure normal record still indexed
    alive_docs = [
        op for op in MockElasticsearchClient.inputs if op["_id"] == "ebsDel002"
    ]
    assert len(alive_docs) == 1
    assert alive_docs[0]["_source"]["title"] == "Alive Title"


def test_transformer_no_changeset_returns_zero(monkeypatch: pytest.MonkeyPatch) -> None:
    event = EbscoAdapterTransformerEvent(changeset_id=None, job_id="20250101T1200")
    config = EbscoAdapterTransformerConfig(
        is_local=True, use_glue_table=False, pipeline_date="dev"
    )

    result = handler(event=event, config_obj=config)
    assert result.batches == []
    assert result.success_count == 0
    assert result.failure_count == 0
    assert MockElasticsearchClient.inputs == []


@pytest.mark.parametrize(
    "event_index_date, pipeline_date, config_index_date, expected_index",
    [
        # 1. Explicit event index date takes precedence (config None)
        ("2025-02-02", "dev", None, "works-source-2025-02-02"),
        # 2. Event index date None -> falls back to pipeline_date (dev)
        (None, "dev", None, "works-source-dev"),
        # 3. Event None, custom pipeline_date used
        (None, "2025-05-05", None, "works-source-2025-05-05"),
        # 4. Event None, config index date set -> uses config index date
        (None, "dev", "2025-07-07", "works-source-2025-07-07"),
        # 5. Event index date overrides config index date when both provided
        ("2025-08-08", "dev", "2025-07-07", "works-source-2025-08-08"),
    ],
)
def test_transformer_index_name_selection(
    temporary_table: pa.Table,
    monkeypatch: pytest.MonkeyPatch,
    event_index_date: str | None,
    pipeline_date: str,
    config_index_date: str | None,
    expected_index: str,
) -> None:
    """Validate full cascade: event.index_date or config.index_date or pipeline_date."""
    _add_pipeline_secrets(pipeline_date)

    # Reset collected inputs between parametrized cases
    with suppress(Exception):  # pragma: no cover - defensive
        MockElasticsearchClient.inputs.clear()

    records_by_id = {
        "ebsIdx001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsIdx001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Some Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    event = EbscoAdapterTransformerEvent(
        changeset_id=changeset_id, index_date=event_index_date, job_id="20250101T1200"
    )
    config = EbscoAdapterTransformerConfig(
        is_local=True,
        use_glue_table=False,
        pipeline_date=pipeline_date,
        index_date=config_index_date,
    )
    handler(event=event, config_obj=config)

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
        SourceWork(id="id1", title="Title 1"),
        SourceWork(id="id2", title="Title 2"),
    ]
    dummy_client = cast(Elasticsearch, object())
    success, errors = load_data(
        elastic_client=dummy_client, records=records, index_name="works-source-dev"
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

    records = [SourceWork(id="id1", title="Bad Title")]
    dummy_client = cast(Elasticsearch, object())
    success, errors = load_data(
        elastic_client=dummy_client, records=records, index_name="works-source-dev"
    )

    assert success == 1
    assert errors == 1
