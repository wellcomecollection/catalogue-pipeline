import json
from contextlib import suppress  # for ruff SIM105 fix
from typing import cast  # added for dummy ES client

import pyarrow as pa
import pytest
from elasticsearch import Elasticsearch  # added

import config as adapter_config
from models.step_events import (
    EbscoAdapterTransformerEvent,
    EbscoAdapterTransformerResult,
)
from models.work import SourceWork
from steps.transformer import (
    EbscoAdapterTransformerConfig,
    handler,
    load_data,
    transform,
)
from utils.iceberg import IcebergTableClient
from utils.tracking import ProcessedFileRecord

from .helpers import data_to_namespaced_table
from .test_mocks import (
    MockElasticsearchClient,
    MockSecretsManagerClient,
    MockSmartOpen,
)

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


def test_transformer_short_circuit_when_prior_processed_detected(
    monkeypatch: pytest.MonkeyPatch, temporary_table: pa.Table
) -> None:
    """Transformer should short-circuit using existing tracking file for the same source file.

    We simulate a prior transformed tracking JSON so the handler exits early without indexing.
    """
    file_uri = "s3://bucket/path/file.xml"
    tracking_uri = f"{file_uri}.transformed.json"
    # Existing tracking file with payload including prior batch_file_location
    prior_result = EbscoAdapterTransformerResult(
        job_id="20250101T1200",
        batch_file_location="s3://bucket/path/20250101T1200.json",
        success_count=10,
        failure_count=0,
    )
    tracking_record = ProcessedFileRecord(
        job_id="20250101T1200", step="transformed", payload=prior_result.model_dump()
    )
    MockSmartOpen.mock_s3_file(tracking_uri, json.dumps(tracking_record.model_dump()))

    # Prepare an event that would otherwise trigger processing (with a changeset id)
    event = EbscoAdapterTransformerEvent(
        changeset_id="new-change-456", job_id="20250101T1200", file_location=file_uri
    )
    config = EbscoAdapterTransformerConfig(
        is_local=True, use_glue_table=False, pipeline_date="dev"
    )

    # Run handler
    result = handler(event=event, config_obj=config)

    # Expect short-circuit with preserved prior batch_file_location and no ES interactions
    assert result.batch_file_location == prior_result.batch_file_location
    assert result.success_count == 0
    assert result.failure_count == 0
    assert MockElasticsearchClient.inputs == []


def test_transformer_end_to_end_with_local_table(
    temporary_table: pa.Table, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "ebs00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Parasites, hosts and diseases</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    result = _run_transform(changeset_id, index_date="2025-01-01")

    assert result.batch_file_location is not None
    assert result.success_count == 2
    assert result.failure_count == 0
    # Validate file contents written to mock S3
    batch_contents_path = MockSmartOpen.file_lookup[result.batch_file_location]
    with open(batch_contents_path, encoding="utf-8") as f:
        data = json.loads(f.read())
    assert data == [list(records_by_id.keys())]
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
    assert result.batch_file_location is not None
    assert result.success_count == 2
    batch_contents_path = MockSmartOpen.file_lookup[result.batch_file_location]
    with open(batch_contents_path, encoding="utf-8") as f:
        data = json.loads(f.read())
    assert data == [["ebsDel001", "ebsDel002"]]
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


def test_transformer_full_retransform_when_no_changeset(
    temporary_table: pa.Table, monkeypatch: pytest.MonkeyPatch
) -> None:
    """When no changeset_id is supplied the transformer reprocesses all existing records."""
    # Seed the table with two MARC records via a changeset so they exist in storage.
    seed_records = {
        "ebsFull001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsFull001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Full Title 1</subfield></datafield></record>",
        "ebsFull002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsFull002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Full Title 2</subfield></datafield></record>",
    }
    # Reuse helper to insert (also patches get_local_table to return the real temporary_table)
    _prepare_changeset(temporary_table, monkeypatch, seed_records)

    # Now call handler with no changeset_id -> full re-transform path
    event = EbscoAdapterTransformerEvent(changeset_id=None, job_id="20250101T1200")
    config = EbscoAdapterTransformerConfig(
        is_local=True, use_glue_table=False, pipeline_date="dev"
    )
    result = handler(event=event, config_obj=config)

    assert result.failure_count == 0
    assert result.success_count == 2
    assert result.batch_file_location is not None
    expected_reindex_path = f"s3://{adapter_config.S3_BUCKET}/{adapter_config.BATCH_S3_PREFIX}/reindex.{event.job_id}.ids.json"
    assert result.batch_file_location == expected_reindex_path
    batch_contents_path = MockSmartOpen.file_lookup[result.batch_file_location]
    with open(batch_contents_path, encoding="utf-8") as f:
        data = json.loads(f.read())
    assert data == [["ebsFull001", "ebsFull002"]]


def test_transformer_batch_file_location_with_changeset(
    temporary_table: pa.Table, monkeypatch: pytest.MonkeyPatch
) -> None:
    """When a changeset exists the batch file path uses the changeset pattern under the batches prefix (file_location irrelevant)."""
    job_id = "20250101T1200"
    records_by_id = {
        "ebsReIdx001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>X</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    event = EbscoAdapterTransformerEvent(
        changeset_id=changeset_id,
        job_id=job_id,
    )
    config = EbscoAdapterTransformerConfig(
        is_local=True, use_glue_table=False, pipeline_date="dev"
    )
    result = handler(event=event, config_obj=config)

    expected_path = f"s3://{adapter_config.S3_BUCKET}/{adapter_config.BATCH_S3_PREFIX}/{changeset_id}.{job_id}.ids.json"
    assert result.batch_file_location == expected_path
    batch_contents_path = MockSmartOpen.file_lookup[result.batch_file_location]
    with open(batch_contents_path, encoding="utf-8") as f:
        data = json.loads(f.read())
    # Only one batch with a single id
    assert len(data) == 1 and len(data[0]) == 1


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


def test_transformer_raises_when_batch_file_write_fails(
    temporary_table: pa.Table, monkeypatch: pytest.MonkeyPatch
) -> None:
    """If writing the batch file fails the exception should propagate and fail the step.

    We simulate a write failure by patching smart_open.open to raise an OSError.
    """
    # Prepare a simple changeset with one record to ensure a batch is produced
    records_by_id = {
        "ebsFail001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsFail001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    # Patch smart_open.open to simulate an S3 (or filesystem) write failure
    def failing_open(*args, **kwargs):  # type: ignore[no-untyped-def]
        raise OSError("simulated write failure")

    monkeypatch.setattr("steps.transformer.smart_open.open", failing_open)

    event = EbscoAdapterTransformerEvent(
        changeset_id=changeset_id,
        job_id="20250101T1200",
    )
    config = EbscoAdapterTransformerConfig(
        is_local=True, use_glue_table=False, pipeline_date="dev"
    )

    with pytest.raises(OSError, match="simulated write failure"):
        handler(event=event, config_obj=config)
