import json

import pytest
from pyiceberg.table import Table as IcebergTable

from models.step_events import EbscoAdapterLoaderEvent, EbscoAdapterTransformerEvent
from steps.loader import EbscoAdapterLoaderConfig, handler
from utils.tracking import ProcessedFileRecord

from .test_mocks import MockSmartOpen


class TestLoaderHandler:
    def test_short_circuit_when_prior_processed_detected(self) -> None:
        """Loader should short-circuit using existing tracking file (no monkeypatch)."""
        file_uri = "s3://bucket/path/file.xml"
        tracking_uri = f"{file_uri}.loaded.json"
        # Provide an existing processed-file tracking JSON via MockSmartOpen so that
        # is_file_already_processed() finds it naturally through smart_open.
        record = ProcessedFileRecord(
            job_id="20250101T1200", changeset_id="prev-change-123", step="loaded"
        )

        MockSmartOpen.mock_s3_file(tracking_uri, json.dumps(record.model_dump()))

        event = EbscoAdapterLoaderEvent(job_id="20250101T1200", file_location=file_uri)
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

        event = EbscoAdapterLoaderEvent(job_id="20250101T1200", file_location=file_uri)
        config = EbscoAdapterLoaderConfig(use_glue_table=False)
        
        result = handler(event=event, config_obj=config)

        assert isinstance(result, EbscoAdapterTransformerEvent)
        assert result.changeset_id is not None

        # Verify tracking file written via MockSmartOpen
        tracking_uri = f"{file_uri}.loaded.json"
        assert tracking_uri in MockSmartOpen.file_lookup
        tracking_path = MockSmartOpen.file_lookup[tracking_uri]

        # tracking_path should be a filesystem path string
        assert isinstance(tracking_path, str)
        with open(tracking_path, "r", encoding="utf-8") as f:
            tracking_json = json.load(f)
        assert tracking_json["job_id"] == "20250101T1200"
        assert tracking_json["step"] == "loaded"

        # changeset_id should match what handler returned
        assert tracking_json["changeset_id"] == result.changeset_id
