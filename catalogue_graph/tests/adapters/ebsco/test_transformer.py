import json
from collections.abc import Generator
from datetime import datetime
from typing import Any, cast

import pytest
import smart_open
from elasticsearch import Elasticsearch
from pyiceberg.table import Table as IcebergTable

import adapters.ebsco.config as adapter_config
from adapters.transformers.base_transformer import BaseSource
from adapters.transformers.ebsco_transformer import EbscoTransformer
from adapters.transformers.manifests import TransformerManifest
from adapters.transformers.transformer import TransformerEvent, handler
from adapters.utils.adapter_store import AdapterStore
from models.pipeline.source.work import DeletedSourceWork, VisibleSourceWork
from tests.mocks import MockElasticsearchClient, MockSmartOpen

from .helpers import data_to_namespaced_table

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
        {"id": rid, "content": data, "last_modified": datetime.now()}
        for rid, data in records_by_id.items()
    ]
    pa_table_initial = data_to_namespaced_table(rows)

    client = AdapterStore(temporary_table)

    store_update = client.incremental_update(pa_table_initial, "ebsco")
    assert store_update is not None
    changeset_id = store_update.changeset_id

    assert changeset_id, "Expected a changeset_id to be returned"

    # Ensure transformer uses our temporary table
    monkeypatch.setattr(
        "adapters.ebsco.helpers.build_adapter_table",
        lambda use_rest_api_table, create_if_not_exists: temporary_table,
    )
    return changeset_id


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
        transformer_type="ebsco",
        job_id="20250101T1200",
        changeset_ids=changeset_ids or [],
    )
    return handler(event=event, es_mode="local", use_rest_api_table=False)


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

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2025-01-01",
    )

    assert result.successes.count == 2
    assert result.failures is None
    assert result.job_id == "20250101T1200"
    assert result.changeset_ids == [changeset_id]

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

    titles = {
        op["_source"].get("data", {}).get("title")
        for op in MockElasticsearchClient.inputs
    }
    assert titles == {"How to Avoid Huge Ships", "Parasites, hosts and diseases"}


def test_transformer_end_to_end_multiple_changesets(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_1 = {
        "ebs00001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>How to Avoid Huge Ships</subfield></datafield></record>",
    }
    records_2 = {
        "ebs00002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebs00002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Parasites, hosts and diseases</subfield></datafield></record>",
    }
    changeset_id_1 = _prepare_changeset(temporary_table, monkeypatch, records_1)
    changeset_id_2 = _prepare_changeset(temporary_table, monkeypatch, records_2)

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id_1, changeset_id_2],
        index_date="2025-01-01",
    )

    assert result.successes.count == 2
    assert result.failures is None
    assert result.job_id == "20250101T1200"
    assert result.changeset_ids == [changeset_id_1, changeset_id_2]

    # Validate file contents written to mock S3 (NDJSON)
    batch_path_full = f"s3://{result.successes.batch_file_location.bucket}/{result.successes.batch_file_location.key}"
    batch_contents_path = MockSmartOpen.file_lookup[batch_path_full]
    with open(batch_contents_path, encoding="utf-8") as f:
        lines = [json.loads(line) for line in f if line.strip()]

        # Success lines include sourceIdentifiers and jobId
    assert lines == [
        {
            "sourceIdentifiers": [
                f"Work[ebsco-alt-lookup/{i}]" for i in ["ebs00001", "ebs00002"]
            ],
            "jobId": "20250101T1200",
        }
    ]

    titles = {
        op["_source"].get("data", {}).get("title")
        for op in MockElasticsearchClient.inputs
    }
    assert titles == {"How to Avoid Huge Ships", "Parasites, hosts and diseases"}


def test_transformer_creates_deletedwork_for_empty_content(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    records_by_id = {
        "ebsDel001": "",  # deletion marker
        "ebsDel002": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsDel002</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Alive Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2025-03-01",
    )

    # Both IDs should appear (one DeletedSourceWork, one VisibleSourceWork)
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
    # DeletedReason now an object with type/info
    assert deleted_source["deletedReason"]["info"] == "not found in EBSCO source"
    assert deleted_source["deletedReason"]["type"] == "DeletedFromSource"
    # Ensure normal record still indexed
    alive_docs = [
        op
        for op in MockElasticsearchClient.inputs
        if op["_id"] == "Work[ebsco-alt-lookup/ebsDel002]"
    ]
    assert len(alive_docs) == 1
    # Visible work title nested under data
    assert alive_docs[0]["_source"]["data"]["title"] == "Alive Title"


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
    job_id = "20250101T1200"
    result = _run_transform(
        monkeypatch,
        changeset_ids=[],
        pipeline_date="dev",
    )

    assert result.failures is None
    assert result.successes.count == 2
    assert result.successes.batch_file_location.bucket == adapter_config.S3_BUCKET
    expected_key = f"{adapter_config.BATCH_S3_PREFIX}/reindex.{job_id}.ids.ndjson"
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
            "jobId": job_id,
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

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        pipeline_date="dev",
    )

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
    records_by_id = {
        "ebsIdx001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsIdx001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Some Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)

    MockElasticsearchClient.inputs.clear()
    _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        pipeline_date=pipeline_date,
        index_date=index_date,
    )
    indices = {op["_index"] for op in MockElasticsearchClient.inputs}
    assert indices == {expected_index}


# --------------------------------------------------------------------------------------
# Tests for transform()
# --------------------------------------------------------------------------------------


def test_transform_empty_content_returns_deleted(
    temporary_table: IcebergTable,
) -> None:
    transformer = EbscoTransformer(AdapterStore(temporary_table), [])
    works = list(transformer.transform([{"id": "work1", "content": ""}]))
    assert len(works) == 1
    assert isinstance(works[0], DeletedSourceWork)
    assert not transformer.errors


def test_transform_invalid_xml_records_error(
    temporary_table: IcebergTable,
) -> None:
    transformer = EbscoTransformer(AdapterStore(temporary_table), [])
    works = list(
        transformer.transform([{"id": "work2", "content": "<record><leader>bad"}])
    )
    assert works == []
    assert transformer.errors and transformer.errors[0].stage == "parse"
    assert transformer.errors[0].work_id == "work2"


def test_transformer_creates_failure_manifest_for_parse_errors(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    """A malformed MARC record should produce a failure manifest with parse_error reason."""
    records_by_id = {
        "ebsBadXml001": "<record><leader>bad",  # malformed
        "ebsGood001": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>ebsGood001</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Valid Title</subfield></datafield></record>",
    }
    changeset_id = _prepare_changeset(temporary_table, monkeypatch, records_by_id)
    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2025-09-01",
    )

    # We expect one success (good record) and one transform error
    assert result.successes.count == 1
    assert result.failures is not None
    assert result.failures.count >= 1

    # Read the failure file
    failure_path_full = f"s3://{result.failures.error_file_location.bucket}/{result.failures.error_file_location.key}"
    failure_contents_path = MockSmartOpen.file_lookup[failure_path_full]
    with open(failure_contents_path, encoding="utf-8") as f:
        failure_lines = [json.loads(line) for line in f if line.strip()]

    # Expect at least one parse error line referencing the bad XML id
    assert any(line["work_id"] == "ebsBadXml001" for line in failure_lines)


def test_transformer_creates_failure_manifest_for_index_errors(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Simulate ES indexing errors and ensure they are captured in failure manifest."""
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

    result = _run_transform(
        monkeypatch,
        changeset_ids=[changeset_id],
        index_date="2025-09-02",
    )
    assert result.failures is not None
    assert result.failures.count == 1

    failure_path_full = f"s3://{result.failures.error_file_location.bucket}/{result.failures.error_file_location.key}"
    failure_contents_path = MockSmartOpen.file_lookup[failure_path_full]
    with open(failure_contents_path, encoding="utf-8") as f:
        failure_lines = [json.loads(line) for line in f if line.strip()]
    # Expect a single failure line with id and detail containing error type
    assert len(failure_lines) == 1
    assert failure_lines[0]["work_id"] == "Work[ebsco-alt-lookup/ebsIdxErr001]"
    assert "mapper_parsing_exception" in failure_lines[0]["detail"]


def test_transform_valid_marcxml_returns_work(
    temporary_table: IcebergTable,
) -> None:
    xml = (
        "<record>"
        "<leader>00000nam a2200000   4500</leader>"
        "<controlfield tag='001'>ebs12345</controlfield>"
        "<datafield tag='245' ind1='0' ind2='0'>"
        "<subfield code='a'>A Useful Title</subfield>"
        "</datafield>"
        "</record>"
    )
    transformer = EbscoTransformer(AdapterStore(temporary_table), [])
    works = list(transformer.transform([{"id": "ebs12345", "content": xml}]))
    assert len(works) == 1
    assert isinstance(works[0], VisibleSourceWork)
    assert works[0].data.title == "A Useful Title"


def test_transform_handles_transform_record_exception(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    def raising_transform_record(_record: Any) -> None:
        raise ValueError("boom: bad data")

    monkeypatch.setattr(
        "adapters.transformers.ebsco_transformer.transform_record",
        raising_transform_record,
    )

    transformer = EbscoTransformer(AdapterStore(temporary_table), [])
    xml = (
        "<record>"
        "<leader>00000nam a2200000   4500</leader>"
        "<controlfield tag='001'>ebsErr123</controlfield>"
        "<datafield tag='245' ind1='0' ind2='0'>"
        "<subfield code='a'>Will Fail</subfield>"
        "</datafield>"
        "</record>"
    )
    works = list(transformer.transform([{"id": "ebsErr123", "content": xml}]))

    assert works == []
    assert transformer.errors
    assert transformer.errors[0].stage == "transform"
    assert "boom: bad data" in transformer.errors[0].detail


class _StubSource(BaseSource):
    def __init__(self, rows: list[dict[str, Any]]):
        self.rows = rows

    def stream_raw(self) -> Generator[dict]:
        yield from self.rows


def test_stream_to_index_success_no_errors(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    rows = [
        {
            "id": "id1",
            "content": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>id1</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Title 1</subfield></datafield></record>",
        },
        {
            "id": "id2",
            "content": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>id2</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Title 2</subfield></datafield></record>",
        },
    ]

    transformer = EbscoTransformer(AdapterStore(temporary_table), [])
    transformer.source = _StubSource(rows)

    es_client = MockElasticsearchClient({}, "")
    transformer.stream_to_index(cast(Elasticsearch, es_client), "works-source-dev")

    assert {a["_id"] for a in MockElasticsearchClient.inputs} == {
        "Work[ebsco-alt-lookup/id1]",
        "Work[ebsco-alt-lookup/id2]",
    }
    assert {a["_source"]["data"]["title"] for a in MockElasticsearchClient.inputs} == {
        "Title 1",
        "Title 2",
    }
    assert not transformer.errors


def test_stream_to_index_with_errors(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    def fake_bulk(client, actions, raise_on_error, stats_only):  # type: ignore[no-untyped-def]
        actions_list = list(actions)
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

    rows = [
        {
            "id": "id1",
            "content": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>id1</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Bad Title</subfield></datafield></record>",
        }
    ]

    transformer = EbscoTransformer(AdapterStore(temporary_table), [])
    transformer.source = _StubSource(rows)

    es_client = MockElasticsearchClient({}, "")
    transformer.stream_to_index(cast(Elasticsearch, es_client), "works-source-dev")

    assert transformer.errors
    assert transformer.errors[0].work_id == "Work[ebsco-alt-lookup/id1]"
    assert "mapper_parsing_exception" in transformer.errors[0].detail


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

    monkeypatch.setattr(smart_open, "open", failing_open)

    with pytest.raises(OSError, match="simulated write failure"):
        _run_transform(
            monkeypatch,
            changeset_ids=[changeset_id],
            pipeline_date="dev",
        )
