from __future__ import annotations

import datetime
from unittest.mock import Mock

import pyarrow as pa
from lxml import etree
from oai_pmh_client.models import Header, Record

from adapters.extractors.oai_pmh.record_writer import (
    BufferedRecordWriter,
    RecordWriter,
    build_adapter_store_row,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA


class TestRecordWriterMocked:
    def test_writes_records_to_store(self) -> None:
        mock_store = Mock(spec=AdapterStore)
        mock_store.incremental_update.return_value = Mock(
            changeset_id="123", upserted_record_ids=["rec1"]
        )

        writer = RecordWriter(
            namespace="test_namespace",
            table_client=mock_store,
            job_id="test_job",
            extra_tags={"window_range": "2023-01-01-2023-01-02"},
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

        assert result.tags == {
            "job_id": "test_job",
            "window_range": "2023-01-01-2023-01-02",
        }
        assert result.changeset_id == "123"
        assert result.upserted_record_ids == ["rec1"]

        mock_store.incremental_update.assert_called_once()
        call_args = mock_store.incremental_update.call_args
        table = call_args[0][0]

        assert isinstance(table, pa.Table)
        assert table.schema.equals(ADAPTER_STORE_ARROW_SCHEMA)
        assert table.num_rows == 1

        row = table.to_pylist()[0]
        assert row["namespace"] == "test_namespace"
        assert row["id"] == "rec1"
        assert row["last_modified"] == datetime.datetime(
            2023, 1, 1, 12, 0, 0, tzinfo=datetime.UTC
        )
        assert "<payload>some content</payload>" in row["content"]
        assert row["deleted"] is False

    def test_handles_empty_records(self) -> None:
        mock_store = Mock(spec=AdapterStore)

        writer = RecordWriter(
            namespace="test_namespace",
            table_client=mock_store,
            job_id="test_job",
            extra_tags={"window_range": "2023-01-01-2023-01-02"},
        )

        result = writer([])

        assert result.tags == {
            "job_id": "test_job",
            "window_range": "2023-01-01-2023-01-02",
        }
        assert result.changeset_id is None
        assert result.upserted_record_ids == []

        mock_store.incremental_update.assert_not_called()

    def test_handles_deleted_records(self) -> None:
        mock_store = Mock(spec=AdapterStore)
        mock_store.incremental_update.return_value = Mock(
            changeset_id="456", upserted_record_ids=["rec1"]
        )

        writer = RecordWriter(
            namespace="test_namespace",
            table_client=mock_store,
            job_id="test_job",
            extra_tags={"window_range": "2023-01-01-2023-01-02"},
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


def _record(identifier: str, *, content: str | None = "some content") -> Record:
    header = Header(
        identifier=identifier,
        datestamp=datetime.datetime(2023, 1, 1, 12, 0, 0, tzinfo=datetime.UTC),
        set_specs=[],
        status=None,
    )
    if content is None:
        return Record(header=header, metadata=None, about=None)

    metadata = etree.Element("metadata")
    payload = etree.SubElement(metadata, "payload")
    payload.text = content
    return Record(header=header, metadata=metadata, about=None)


class TestBuildAdapterStoreRow:
    def test_serializes_a_record(self) -> None:
        row = build_adapter_store_row(
            namespace="ns", identifier="rec1", record=_record("rec1")
        )

        assert row["namespace"] == "ns"
        assert row["id"] == "rec1"
        assert "some content" in row["content"]
        assert row["deleted"] is False
        assert row["last_modified"] == datetime.datetime(
            2023, 1, 1, 12, 0, 0, tzinfo=datetime.UTC
        )

    def test_record_without_metadata_is_a_tombstone(self) -> None:
        row = build_adapter_store_row(
            namespace="ns", identifier="rec1", record=_record("rec1", content=None)
        )

        assert row["content"] is None
        assert row["deleted"] is True


class TestBufferedWriterFlushThreshold:
    def _writer(self, mock_store: Mock, threshold: int | None) -> BufferedRecordWriter:
        return BufferedRecordWriter(
            namespace="ns",
            table_client=mock_store,
            job_id="job",
            flush_threshold=threshold,
        )

    def test_commits_once_the_threshold_is_reached(self) -> None:
        mock_store = Mock(spec=AdapterStore)
        mock_store.incremental_update.return_value = Mock(
            changeset_id="cs1", upserted_record_ids=["a", "b"]
        )
        writer = self._writer(mock_store, 2)

        writer([("a", _record("a"))])
        assert mock_store.incremental_update.call_count == 0
        assert writer.pending == 1

        writer([("b", _record("b"))])
        assert mock_store.incremental_update.call_count == 1
        assert writer.pending == 0

    def test_no_threshold_never_auto_flushes(self) -> None:
        mock_store = Mock(spec=AdapterStore)
        writer = self._writer(mock_store, None)

        for identifier in ["a", "b", "c"]:
            writer([(identifier, _record(identifier))])

        assert mock_store.incremental_update.call_count == 0
        assert writer.pending == 3

    def test_changeset_ids_accumulate_across_flushes(self) -> None:
        mock_store = Mock(spec=AdapterStore)
        mock_store.incremental_update.side_effect = [
            Mock(changeset_id="cs1", upserted_record_ids=["a"]),
            Mock(changeset_id="cs2", upserted_record_ids=["b"]),
        ]
        writer = self._writer(mock_store, 1)

        writer([("a", _record("a"))])
        writer([("b", _record("b"))])

        assert writer.changeset_ids == ["cs1", "cs2"]
        assert writer.upserted_record_count == 2

    def test_noop_update_contributes_no_changeset_id(self) -> None:
        mock_store = Mock(spec=AdapterStore)
        mock_store.incremental_update.return_value = None
        writer = self._writer(mock_store, 1)

        writer([("a", _record("a"))])

        assert writer.changeset_ids == []
        assert writer.upserted_record_count == 0

    def test_omits_window_range_tag_when_absent(self) -> None:
        mock_store = Mock(spec=AdapterStore)
        writer = self._writer(mock_store, None)

        result = writer([("a", _record("a"))])

        assert result.tags == {"job_id": "job"}
