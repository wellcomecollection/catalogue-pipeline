from __future__ import annotations

from collections.abc import Iterable, Iterator
from datetime import UTC, datetime, tzinfo
from typing import TextIO
from unittest.mock import MagicMock, patch

import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.ebsco.marcxml_loader import MarcXmlFileLoader
from adapters.ebsco.models.step_events import EbscoAdapterLoaderEvent, LoaderResponse
from adapters.ebsco.steps import loader
from adapters.ebsco.steps.loader import EBSCO_NAMESPACE, LoaderRuntime
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA
from tests.mocks import MockSmartOpen


def _register_mock_open(path: str) -> None:
    with open(path, "rb") as fh:
        MockSmartOpen.mock_s3_file(path, fh.read())


def _runtime_with(table: IcebergTable) -> LoaderRuntime:
    return LoaderRuntime(
        adapter_store=AdapterStore(table, default_namespace=EBSCO_NAMESPACE),
        marcxml_loader=MarcXmlFileLoader(
            schema=ARROW_SCHEMA, namespace=EBSCO_NAMESPACE
        ),
    )


def _ids(table_rows: Iterable[dict]) -> set[str]:
    return {row["id"] for row in table_rows}


def _patch_datetime(
    monkeypatch: pytest.MonkeyPatch, timestamps: Iterator[datetime]
) -> None:
    class _StubDateTime(datetime):
        @classmethod
        def now(cls, tz: tzinfo | None = None) -> _StubDateTime:
            try:
                value = next(timestamps)
                return cls.fromtimestamp(value.timestamp(), tz=value.tzinfo)
            except StopIteration:
                # Reuse last value if more calls occur
                return cls(1970, 1, 1, tzinfo=tz) if tz else cls(1970, 1, 1)

    monkeypatch.setattr("adapters.utils.adapter_store.datetime", _StubDateTime)


def test_execute_loader_inserts_records(
    temporary_table: IcebergTable, xml_with_two_records: TextIO
) -> None:
    runtime = _runtime_with(temporary_table)
    _register_mock_open(xml_with_two_records.name)

    request = EbscoAdapterLoaderEvent(
        file_location=xml_with_two_records.name, job_id="job-123"
    )
    response = loader.execute_loader(request, runtime=runtime)

    assert response.job_id == "job-123"
    assert len(response.changeset_ids) == 1

    records = runtime.adapter_store.get_all_records()
    rows = records.to_pylist()
    assert _ids(rows) == {"ebs00001", "ebs00002"}
    assert set(records.column("changeset").to_pylist()) == set(response.changeset_ids)
    assert response.changed_record_count == 2


def test_execute_loader_updates_existing_records(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    xml_with_one_record: TextIO,
    xml_with_three_records: TextIO,
) -> None:
    ts1 = datetime(2025, 1, 2, 10, 0, tzinfo=UTC)
    ts2 = datetime(2025, 1, 2, 10, 5, tzinfo=UTC)
    _patch_datetime(monkeypatch, iter([ts1, ts2]))

    runtime = _runtime_with(temporary_table)

    _register_mock_open(xml_with_one_record.name)
    initial_request = EbscoAdapterLoaderEvent(
        file_location=xml_with_one_record.name, job_id="job-123"
    )
    initial_response = loader.execute_loader(initial_request, runtime=runtime)

    _register_mock_open(xml_with_three_records.name)
    update_request = EbscoAdapterLoaderEvent(
        file_location=xml_with_three_records.name, job_id="job-123"
    )
    update_response = loader.execute_loader(update_request, runtime=runtime)

    assert update_response.changeset_ids != []
    assert update_response.changeset_ids != initial_response.changeset_ids

    rows = runtime.adapter_store.get_all_records().to_pylist()
    assert _ids(rows) == {"ebs00001", "ebs00003", "ebs00004"}

    record_one = next(row for row in rows if row["id"] == "ebs00001")
    assert "John W. Trimmer" in record_one["content"]
    assert record_one["last_modified"] == ts2
    assert all(row["last_modified"] == ts2 for row in rows)
    assert update_response.changed_record_count == 3


def test_execute_loader_soft_deletes_missing_records(
    temporary_table: IcebergTable,
    xml_with_two_records: TextIO,
    xml_with_one_record: TextIO,
) -> None:
    runtime = _runtime_with(temporary_table)

    _register_mock_open(xml_with_two_records.name)
    initial_request = EbscoAdapterLoaderEvent(
        file_location=xml_with_two_records.name, job_id="job-123"
    )
    loader.execute_loader(initial_request, runtime=runtime)

    _register_mock_open(xml_with_one_record.name)
    delete_request = EbscoAdapterLoaderEvent(
        file_location=xml_with_one_record.name, job_id="job-123"
    )
    delete_response = loader.execute_loader(delete_request, runtime=runtime)

    assert delete_response.changeset_ids != []

    visible_rows = runtime.adapter_store.get_all_records().to_pylist()
    assert _ids(visible_rows) == {"ebs00001"}

    all_rows = runtime.adapter_store.get_all_records(include_deleted=True).to_pylist()
    deleted_record = next(row for row in all_rows if row["id"] == "ebs00002")

    assert deleted_record["content"] is None
    assert deleted_record["changeset"] in delete_response.changeset_ids
    assert delete_response.changed_record_count == 1


def test_last_modified_updates_on_content_change(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    xml_with_one_record: TextIO,
    xml_with_three_records: TextIO,
) -> None:
    ts1 = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)
    ts2 = datetime(2025, 1, 1, 12, 1, tzinfo=UTC)
    _patch_datetime(monkeypatch, iter([ts1, ts2]))

    runtime = _runtime_with(temporary_table)

    _register_mock_open(xml_with_one_record.name)
    loader.execute_loader(
        EbscoAdapterLoaderEvent(
            file_location=xml_with_one_record.name, job_id="job-123"
        ),
        runtime=runtime,
    )

    record = runtime.adapter_store.get_all_records().to_pylist()[0]
    assert record["last_modified"] == ts1

    _register_mock_open(xml_with_three_records.name)
    update_response = loader.execute_loader(
        EbscoAdapterLoaderEvent(
            file_location=xml_with_three_records.name, job_id="job-123"
        ),
        runtime=runtime,
    )

    rows = runtime.adapter_store.get_all_records().to_pylist()
    record_one = next(row for row in rows if row["id"] == "ebs00001")
    assert record_one["last_modified"] == ts2
    assert all(row["last_modified"] == ts2 for row in rows)
    assert rows[0]["changeset"] == rows[1]["changeset"] == rows[2]["changeset"]
    assert rows[0]["changeset"] in update_response.changeset_ids
    assert update_response.changed_record_count == 3


def test_last_modified_on_delete_marks_removed_records_newer(
    monkeypatch: pytest.MonkeyPatch,
    temporary_table: IcebergTable,
    xml_with_two_records: TextIO,
    xml_with_one_record: TextIO,
) -> None:
    ts1 = datetime(2025, 1, 1, 13, 0, tzinfo=UTC)
    ts2 = datetime(2025, 1, 1, 13, 5, tzinfo=UTC)
    _patch_datetime(monkeypatch, iter([ts1, ts2]))

    runtime = _runtime_with(temporary_table)

    _register_mock_open(xml_with_two_records.name)
    loader.execute_loader(
        EbscoAdapterLoaderEvent(
            file_location=xml_with_two_records.name, job_id="job-123"
        ),
        runtime=runtime,
    )

    rows_initial = runtime.adapter_store.get_all_records(
        include_deleted=True
    ).to_pylist()
    assert all(row["last_modified"] == ts1 for row in rows_initial)

    _register_mock_open(xml_with_one_record.name)
    delete_response = loader.execute_loader(
        EbscoAdapterLoaderEvent(
            file_location=xml_with_one_record.name, job_id="job-123"
        ),
        runtime=runtime,
    )

    rows_after = runtime.adapter_store.get_all_records(include_deleted=True).to_pylist()
    kept = next(row for row in rows_after if row["id"] == "ebs00001")
    deleted = next(row for row in rows_after if row["id"] == "ebs00002")

    assert kept["last_modified"] == ts1  # unchanged record retains original timestamp
    assert deleted["last_modified"] == ts2  # delete marker stamped later
    assert deleted["content"] is None
    assert delete_response.changed_record_count == 1


def test_handler_publishes_loader_report(
    temporary_table: IcebergTable, xml_with_two_records: TextIO
) -> None:
    runtime = _runtime_with(temporary_table)
    _register_mock_open(xml_with_two_records.name)

    req = EbscoAdapterLoaderEvent(
        file_location=xml_with_two_records.name, job_id="job-456"
    )

    mock_report = MagicMock()

    with patch.object(loader.EbscoLoaderReport, "from_loader") as mock_from_loader:
        mock_from_loader.return_value = mock_report

        loader_response = loader.handler(req, runtime=runtime)

    assert mock_from_loader.call_count == 1
    called_event, response_for_report = mock_from_loader.call_args.args
    assert called_event == req
    assert isinstance(response_for_report, LoaderResponse)
    mock_report.publish.assert_called_once()
    assert response_for_report.changed_record_count == 2
    assert response_for_report.changeset_ids
    assert loader_response == response_for_report
