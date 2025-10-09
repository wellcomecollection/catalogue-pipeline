import json
from contextlib import suppress  # for ruff SIM105 fix
from typing import Any, cast  # added for dummy ES client

import pytest
import smart_open
from pyiceberg.table import Table as IcebergTable

import adapters.ebsco.config as adapter_config
from adapters.ebsco.models.manifests import TransformerManifest
from adapters.ebsco.models.step_events import EbscoAdapterTransformerEvent
from adapters.ebsco.models.work import SourceIdentifier, SourceWork
from adapters.ebsco.steps.transformer import (
    EbscoAdapterTransformerConfig,
    handler,
    load_data,
    transform,
)
from adapters.ebsco.utils.iceberg import IcebergTableClient

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
    temporary_table: IcebergTable,
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
        "adapters.ebsco.utils.iceberg.get_local_table", lambda **kwargs: temporary_table
    )
    return changeset_id


def _run_transform(
    changeset_id: str,
    *,
    index_date: str | None = None,
    pipeline_date: str = "dev",
) -> TransformerManifest:
    event = EbscoAdapterTransformerEvent(
        changeset_id=changeset_id,
        job_id="20250101T1200",
    )
    config = EbscoAdapterTransformerConfig(
        is_local=True,
        use_rest_api_table=False,
        pipeline_date=pipeline_date,
        index_date=index_date,
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
        ("transformer/api_key", ""),
    ]:
        MockSecretsManagerClient.add_mock_secret(f"{secret_prefix}/{name}", value)


# --------------------------------------------------------------------------------------
# Tests
# --------------------------------------------------------------------------------------


def test_transformer_end_to_end_with_local_table(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
        "ebs00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Parasites, hosts and diseases</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    result = _run_transform(changeset_id, index_date="2025-01-01")

    assert result.successes.count == 2
    assert result.failures is None
    # Validate file contents written to mock S3 (NDJSON)
    batch_path_full = f"s3://{result.successes.batch_file_location.bucket}/{result.successes.batch_file_location.key}"
    batch_contents_path = MockSmartOpen.file_lookup[batch_path_full]
    with open(batch_contents_path, encoding="utf-8") as f:
        lines = [json.loads(line) for line in f if line.strip()]
    # Success lines include sourceIdentifiers and jobId
    assert lines == [
        {
            "sourceIdentifiers": [f"Work[ebsco-alt-lookup/{i}]" for i in records_by_id],
            "jobId": "20250101T1200",
        }
    ]
    titles = {op["_source"].get("title") for op in MockElasticsearchClient.inputs}
    assert titles == {"How to Avoid Huge Ships", "Parasites, hosts and diseases"}


def test_transformer_creates_deletedwork_for_empty_content(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    # Note: empty string content indicates deletion
    records_by_id = {
        "ebsDel001": "",  # deletion marker
        "ebsDel002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsDel002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Alive Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    result = _run_transform(changeset_id, index_date="2025-03-01")

    # Both IDs should appear (one DeletedWork, one SourceWork)
    assert result.successes.count == 2
    assert result.failures is None
    batch_path_full = f"s3://{result.successes.batch_file_location.bucket}/{result.successes.batch_file_location.key}"
    batch_contents_path = MockSmartOpen.file_lookup[batch_path_full]
    with open(batch_contents_path, encoding="utf-8") as f:
        lines = [json.loads(line) for line in f if line.strip()]
    assert lines == [
        {
            "sourceIdentifiers": [
                "Work[ebsco-alt-lookup/ebsDel001]",
                "Work[ebsco-alt-lookup/ebsDel002]",
            ],
            "jobId": "20250101T1200",
        }
    ]
    deleted_docs = [
        op
        for op in MockElasticsearchClient.inputs
        if op["_id"] == "Work[ebsco-alt-lookup/ebsDel001]"
    ]
    assert len(deleted_docs) == 1
    deleted_source = deleted_docs[0]["_source"]
    assert deleted_source["deletedReason"] == "not found in EBSCO source"
    # Ensure normal record still indexed
    alive_docs = [
        op
        for op in MockElasticsearchClient.inputs
        if op["_id"] == "Work[ebsco-alt-lookup/ebsDel002]"
    ]
    assert len(alive_docs) == 1
    assert alive_docs[0]["_source"]["title"] == "Alive Title"


def test_transformer_full_retransform_when_no_changeset(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
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
        is_local=True, use_rest_api_table=False, pipeline_date="dev"
    )
    result = handler(event=event, config_obj=config)

    assert result.failures is None
    assert result.successes.count == 2
    assert result.successes.batch_file_location.bucket == adapter_config.S3_BUCKET
    expected_key = f"{adapter_config.BATCH_S3_PREFIX}/reindex.{event.job_id}.ids.ndjson"
    assert result.successes.batch_file_location.key == expected_key
    batch_path_full = f"s3://{result.successes.batch_file_location.bucket}/{result.successes.batch_file_location.key}"
    batch_contents_path = MockSmartOpen.file_lookup[batch_path_full]
    with open(batch_contents_path, encoding="utf-8") as f:
        lines = [json.loads(line) for line in f if line.strip()]
    assert lines == [
        {
            "sourceIdentifiers": [
                "Work[ebsco-alt-lookup/ebsFull001]",
                "Work[ebsco-alt-lookup/ebsFull002]",
            ],
            "jobId": event.job_id,
        }
    ]


def test_transformer_batch_file_key_with_changeset(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    """When a changeset exists the batch file path uses the changeset pattern under the batches prefix."""
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
        is_local=True, use_rest_api_table=False, pipeline_date="dev"
    )
    result = handler(event=event, config_obj=config)

    expected_key = (
        f"{adapter_config.BATCH_S3_PREFIX}/{changeset_id}.{job_id}.ids.ndjson"
    )
    assert result.successes.batch_file_location.bucket == adapter_config.S3_BUCKET
    assert result.successes.batch_file_location.key == expected_key
    batch_path_full = f"s3://{result.successes.batch_file_location.bucket}/{result.successes.batch_file_location.key}"
    batch_contents_path = MockSmartOpen.file_lookup[batch_path_full]
    with open(batch_contents_path, encoding="utf-8") as f:
        lines = [json.loads(line) for line in f if line.strip()]
    # Only one batch line with a single sourceIdentifier
    assert len(lines) == 1 and len(lines[0]["sourceIdentifiers"]) == 1


@pytest.mark.parametrize(
    "pipeline_date, index_date, expected_index",
    [
        ("dev", None, "works-source-dev"),
        ("2025-05-05", None, "works-source-2025-05-05"),
        ("dev", "2025-07-07", "works-source-2025-07-07"),
    ],
)
def test_transformer_index_name_selection(
    temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
    pipeline_date: str,
    index_date: str | None,
    expected_index: str,
) -> None:
    """Validate cascade: config.index_date or pipeline_date (event no longer provides override)."""
    _add_pipeline_secrets(pipeline_date)

    with suppress(Exception):  # pragma: no cover - defensive
        MockElasticsearchClient.inputs.clear()

    records_by_id = {
        "ebsIdx001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsIdx001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Some Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    event = EbscoAdapterTransformerEvent(
        changeset_id=changeset_id, job_id="20250101T1200"
    )
    config = EbscoAdapterTransformerConfig(
        is_local=True,
        use_rest_api_table=False,
        pipeline_date=pipeline_date,
        index_date=index_date,
    )
    handler(event=event, config_obj=config)

    indices = {op["_index"] for op in MockElasticsearchClient.inputs}
    assert indices == {expected_index}


# --------------------------------------------------------------------------------------
# Tests for transform()
# --------------------------------------------------------------------------------------


def test_transform_empty_content_returns_error() -> None:
    works, errors = transform("work1", "")
    assert works == []
    assert errors and errors[0]["reason"] == "empty_content"
    # IDs in errors are now wrapped
    assert errors[0]["id"] == "work1"


def test_transform_invalid_xml_returns_parse_error() -> None:
    works, errors = transform("work2", "<record><leader>bad")
    assert works == []
    assert errors and errors[0]["reason"] == "parse_error"
    assert errors[0]["id"] == "work2"


def test_transformer_creates_failure_manifest_for_parse_errors(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    """A malformed MARC record should produce a failure manifest with parse_error reason."""
    records_by_id = {
        "ebsBadXml001": "<record><leader>bad",  # malformed
        "ebsGood001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsGood001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Valid Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)
    result = _run_transform(changeset_id, index_date="2025-09-01")

    # We expect one success (good record) and one transform error
    assert result.successes.count == 1
    assert result.failures is not None
    assert result.failures.count >= 1

    # Read the failure file
    failure_path_full = f"s3://{result.failures.error_file_location.bucket}/{result.failures.error_file_location.key}"
    failure_contents_path = MockSmartOpen.file_lookup[failure_path_full]
    with open(failure_contents_path, encoding="utf-8") as f:
        failure_lines = [json.loads(line) for line in f if line.strip()]

    # Expect exactly one parse error line referencing the bad XML id
    # Failure lines now have shape {id, message}; parse_error must appear in message
    assert any(
        line["id"] == "ebsBadXml001" and "reason=parse_error" in line["message"]
        for line in failure_lines
    )


def test_transformer_creates_failure_manifest_for_index_errors(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Simulate ES indexing errors and ensure they are captured in failure manifest."""
    # Create a single valid record
    records_by_id = {
        "ebsIdxErr001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsIdxErr001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Index Error Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    # Monkeypatch bulk to force an indexing error
    def fake_bulk(client, actions, raise_on_error, stats_only):  # type: ignore[no-untyped-def]
        actions_list = list(actions)
        # pretend success count is number of actions even with error
        return len(actions_list), [
            {
                "index": {
                    "_id": actions_list[0]["_id"],
                    "status": 400,
                    "error": {"type": "mapper_parsing_exception"},
                }
            }
        ]

    monkeypatch.setattr("elasticsearch.helpers.bulk", fake_bulk)

    result = _run_transform(changeset_id, index_date="2025-09-02")
    assert result.failures is not None
    assert result.failures.count == 1

    failure_path_full = f"s3://{result.failures.error_file_location.bucket}/{result.failures.error_file_location.key}"
    failure_contents_path = MockSmartOpen.file_lookup[failure_path_full]
    with open(failure_contents_path, encoding="utf-8") as f:
        failure_lines = [json.loads(line) for line in f if line.strip()]
    # Expect a single failure line with id and message containing status & error_type
    assert len(failure_lines) == 1
    msg = failure_lines[0]["message"]
    assert failure_lines[0]["id"] == "Work[ebsco-alt-lookup/ebsIdxErr001]"
    assert "status=400" in msg and "error_type=mapper_parsing_exception" in msg


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
    works, errors = transform("ebs12345", xml)
    assert not errors
    assert len(works) == 1
    assert str(works[0].source_identifier) == "Work[ebsco-alt-lookup/ebs12345]"
    assert works[0].title == "A Useful Title"


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

    IDENT_TYPE = "ebsco-alt-lookup"
    records = [
        SourceWork(
            title="Title 1",
            source_identifier=SourceIdentifier(
                identifier_type=IDENT_TYPE, ontology_type="Work", value="id1"
            ),
        ),
        SourceWork(
            title="Title 2",
            source_identifier=SourceIdentifier(
                identifier_type=IDENT_TYPE, ontology_type="Work", value="id2"
            ),
        ),
    ]
    dummy_client = cast(Any, object())
    success, errors = load_data(
        elastic_client=dummy_client, records=records, index_name="works-source-dev"
    )

    assert success == 2
    assert errors == []
    assert {a["_id"] for a in collected} == {
        "Work[ebsco-alt-lookup/id1]",
        "Work[ebsco-alt-lookup/id2]",
    }
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

    IDENT_TYPE = "ebsco-alt-lookup"
    records = [
        SourceWork(
            title="Bad Title",
            source_identifier=SourceIdentifier(
                identifier_type=IDENT_TYPE, ontology_type="Work", value="id1"
            ),
        )
    ]
    dummy_client = cast(Any, object())
    success, errors = load_data(
        elastic_client=dummy_client, records=records, index_name="works-source-dev"
    )

    assert success == 1
    assert len(errors) == 1
    assert errors[0]["error_type"] == "mapper_parsing_exception"


def test_transformer_raises_when_batch_file_write_fails(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
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

    # Patch smart_open globally; transformer references the imported module so this applies
    monkeypatch.setattr(smart_open, "open", failing_open)

    event = EbscoAdapterTransformerEvent(
        changeset_id=changeset_id,
        job_id="20250101T1200",
    )
    config = EbscoAdapterTransformerConfig(
        is_local=True, use_rest_api_table=False, pipeline_date="dev"
    )

    with pytest.raises(OSError, match="simulated write failure"):
        handler(event=event, config_obj=config)
