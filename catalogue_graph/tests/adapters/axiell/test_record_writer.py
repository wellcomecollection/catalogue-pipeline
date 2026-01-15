import datetime
from unittest.mock import Mock

import pyarrow as pa
from lxml import etree
from oai_pmh_client.models import Header, Record

from adapters.axiell.record_writer import WindowRecordWriter
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA


def test_writes_records_to_store() -> None:
    mock_store = Mock(spec=AdapterStore)
    mock_store.incremental_update.return_value = Mock(
        changeset_id="123", updated_record_ids=["rec1"]
    )

    writer = WindowRecordWriter(
        namespace="test_namespace",
        table_client=mock_store,
        job_id="test_job",
        window_range="2023-01-01-2023-01-02",
    )

    record_header = Header(
        identifier="rec1",
        datestamp=datetime.datetime(2023, 1, 1, 12, 0, 0, tzinfo=datetime.UTC),
        set_specs=[],
        status=None,
    )

    xml_metadata = etree.Element("metadata")
    payload = etree.SubElement(xml_metadata, "payload")
    payload.text = "some content"

    record = Record(header=record_header, metadata=xml_metadata, about=None)

    result = writer([("rec1", record)])

    assert result["tags"] == {
        "job_id": "test_job",
        "window_range": "2023-01-01-2023-01-02",
        "changeset_id": "123",
        "record_ids_changed": '["rec1"]',
    }

    mock_store.incremental_update.assert_called_once()
    call_args = mock_store.incremental_update.call_args
    table = call_args[0][0]

    assert isinstance(table, pa.Table)
    assert table.schema.equals(ARROW_SCHEMA)
    assert table.num_rows == 1

    row = table.to_pylist()[0]
    assert row["namespace"] == "test_namespace"
    assert row["id"] == "rec1"
    assert row["last_modified"] == datetime.datetime(
        2023, 1, 1, 12, 0, 0, tzinfo=datetime.UTC
    )
    assert "<payload>some content</payload>" in row["content"]
    assert row["deleted"] is False


def test_handles_empty_records() -> None:
    mock_store = Mock(spec=AdapterStore)

    writer = WindowRecordWriter(
        namespace="test_namespace",
        table_client=mock_store,
        job_id="test_job",
        window_range="2023-01-01-2023-01-02",
    )

    result = writer([])

    assert result["tags"] == {
        "job_id": "test_job",
        "window_range": "2023-01-01-2023-01-02",
    }

    mock_store.incremental_update.assert_not_called()


def test_handles_deleted_records() -> None:
    # Deleted records might have no metadata
    mock_store = Mock(spec=AdapterStore)
    mock_store.incremental_update.return_value = Mock(
        changeset_id="456", updated_record_ids=["rec1"]
    )

    writer = WindowRecordWriter(
        namespace="test_namespace",
        table_client=mock_store,
        job_id="test_job",
        window_range="2023-01-01-2023-01-02",
    )

    record_header = Header(
        identifier="rec1",
        datestamp=datetime.datetime(2023, 1, 1, 12, 0, 0, tzinfo=datetime.UTC),
        set_specs=[],
        status="deleted",
    )

    record = Record(header=record_header, metadata=None, about=None)

    writer([("rec1", record)])

    mock_store.incremental_update.assert_called_once()
    table = mock_store.incremental_update.call_args[0][0]
    row = table.to_pylist()[0]

    assert row["content"] is None
    assert row["deleted"] is True
