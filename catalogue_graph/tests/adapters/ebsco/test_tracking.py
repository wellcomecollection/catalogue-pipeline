import json
from unittest.mock import Mock, patch

from adapters.ebsco.models.step_events import EbscoAdapterTransformerEvent
from adapters.ebsco.utils.tracking import (
    ProcessedFileRecord,
    is_file_already_processed,
    record_processed_file,
)


class TestRecordProcessedFile:
    def setup_method(self) -> None:
        """Setup method run before each test."""
        self.mock_smart_open = patch(
            "adapters.ebsco.utils.tracking.smart_open.open"
        ).start()
        self.mock_file = Mock()
        self.mock_smart_open.return_value.__enter__.return_value = self.mock_file

    def teardown_method(self) -> None:
        """Teardown method run after each test."""
        patch("adapters.ebsco.utils.tracking.smart_open.open").stop()

    def test_record_processed_file(self) -> None:
        job_id = "test-job-id"
        file_location = "s3://s3-bucket/is-a/file.xml"
        event = EbscoAdapterTransformerEvent(
            changeset_id="I have changed", job_id=job_id
        )
        record = record_processed_file(
            job_id=job_id, file_location=file_location, step="loaded", payload_obj=event
        )

        assert isinstance(record, ProcessedFileRecord)
        assert record.job_id == job_id
        assert record.payload["changeset_id"] == "I have changed"  # type: ignore[index]
        assert record.step == "loaded"
        # Verify smart_open was called with the correct S3 URI
        self.mock_smart_open.assert_called_once_with(
            "s3://s3-bucket/is-a/file.xml.loaded.json", "w", encoding="utf-8"
        )

        # Verify the JSON was written correctly
        # Order of keys in JSON dump isn't guaranteed; compare via parsed dict
        written_args = self.mock_file.write.call_args[0][0]
        data = json.loads(written_args)
        assert data["job_id"] == job_id
        assert data["step"] == "loaded"
        assert data["payload"]["changeset_id"] == "I have changed"


class TestIsFileAlreadyProcessed:
    # No boto3 patching required now that lookup uses smart_open directly.

    def test_file_already_processed(self) -> None:
        file_location = "s3://test-bucket/dev/ftp_v2/existing-file.xml"

        prior_event = EbscoAdapterTransformerEvent(job_id="jid", changeset_id="cid")
        stored = ProcessedFileRecord(
            job_id="jid", step="loaded", payload=prior_event.model_dump()
        )
        with patch("adapters.ebsco.utils.tracking.smart_open.open") as mock_open:
            mock_file = Mock()
            mock_open.return_value.__enter__.return_value = mock_file
            mock_file.read.return_value = json.dumps(stored.model_dump())
            record = is_file_already_processed(file_location, step="loaded")
        assert isinstance(record, ProcessedFileRecord)
        assert record.job_id == "jid"
        assert record.get("changeset_id") == "cid"

    def test_file_not_yet_processed(self) -> None:
        file_location = "s3://test-bucket/dev/ftp_v2/non-existent-file.xml"
        record = is_file_already_processed(file_location, step="loaded")
        assert record is None
