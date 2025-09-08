import pytest
from pyiceberg.table import Table as IcebergTable

from models.step_events import EbscoAdapterLoaderEvent, EbscoAdapterTransformerEvent
from steps.loader import EbscoAdapterLoaderConfig, handler

from .test_mocks import MockSmartOpen


class TestLoaderHandler:
    def test_short_circuit_when_changeset_id_present(self) -> None:
        event = EbscoAdapterLoaderEvent(
            job_id="20250101T1200",
            file_location="s3://bucket/path/file.xml",
            changeset_id="prev-change-123",
        )
        config = EbscoAdapterLoaderConfig(use_glue_table=False)
        result = handler(event=event, config_obj=config)
        assert isinstance(result, EbscoAdapterTransformerEvent)
        assert result.changeset_id == "prev-change-123"

    def test_normal_processing_path_records_file(
        self, monkeypatch: pytest.MonkeyPatch, temporary_table: IcebergTable
    ) -> None:
        # Prepare a minimal XML content with two simple records
        sample_xml = (
            "<collection>"
            "<record><controlfield tag='001'>r1</controlfield></record>"
            "<record><controlfield tag='001'>r2</controlfield></record>"
            "</collection>"
        )
        file_uri = "s3://bucket/path/file.xml"
        # Register content with shared MockSmartOpen (autouse fixture already patched smart_open.open)
        MockSmartOpen.mock_s3_file(file_uri, sample_xml.encode("utf-8"))

        # Use actual temporary Iceberg table (use_glue_table=False path only invokes get_local_table)
        monkeypatch.setattr(
            "steps.loader.get_local_table", lambda **kwargs: temporary_table
        )

        # Capture record_processed_file invocation
        recorded: dict[str, object] = {}

        def fake_record(job_id, file_location, changeset_id, step):  # type: ignore[no-untyped-def]
            recorded.update(
                {
                    "job_id": job_id,
                    "file_location": file_location,
                    "changeset_id": changeset_id,
                    "step": step,
                }
            )

        monkeypatch.setattr("steps.loader.record_processed_file", fake_record)

        event = EbscoAdapterLoaderEvent(
            job_id="20250101T1200",
            file_location=file_uri,
            changeset_id=None,
        )
        config = EbscoAdapterLoaderConfig(use_glue_table=False)
        result = handler(event=event, config_obj=config)

        assert isinstance(result, EbscoAdapterTransformerEvent)
        assert result.changeset_id is not None
        assert recorded.get("step") == "loaded"
        assert recorded.get("file_location") == file_uri
