import json
from unittest.mock import Mock, patch

from adapters.ebsco.utils.tracking import (
    ProcessedFileRecord,
    record_processed_file,
)
from adapters.transformers.transformer import TransformerEvent
from utils.aws import pydantic_from_s3_json


class TestRecordProcessedFile:
    def setup_method(self) -> None:
        """Setup method run before each test."""
        self.mock_smart_open = patch("utils.aws.smart_open.open").start()
        self.mock_file = Mock()
        self.mock_smart_open.return_value.__enter__.return_value = self.mock_file

    def teardown_method(self) -> None:
        """Teardown method run after each test."""
        patch("utils.aws.smart_open.open").stop()

    def test_record_processed_file(self) -> None:
        job_id = "test-job-id"
        file_location = "s3://s3-bucket/is-a/file.xml"
        event = TransformerEvent(
            transformer_type="ebsco",
            job_id=job_id,
            changeset_ids=["I have changed"],
        )
        record = record_processed_file(
            job_id=job_id, file_location=file_location, step="loaded", payload_obj=event
        )

        assert isinstance(record, ProcessedFileRecord)
        assert record.job_id == job_id
        assert record.payload["changeset_ids"] == ["I have changed"]  # type: ignore[index]
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
        assert data["payload"]["changeset_ids"] == ["I have changed"]


class TestIsFileAlreadyProcessed:
    def test_file_already_processed(self) -> None:
        file_location = "s3://test-bucket/dev/ftp_v2/existing-file.xml"

        prior_event = TransformerEvent(
            transformer_type="ebsco",
            job_id="jid",
            changeset_ids=["cid"],
        )
        stored = ProcessedFileRecord(
            job_id="jid", step="loaded", payload=prior_event.model_dump()
        )
        with patch("utils.aws.smart_open.open") as mock_open:
            mock_file = Mock()
            mock_open.return_value.__enter__.return_value = mock_file
            mock_file.read.return_value = json.dumps(stored.model_dump())
            record = pydantic_from_s3_json(
                ProcessedFileRecord, f"{file_location}.loaded.json"
            )
        assert isinstance(record, ProcessedFileRecord)
        assert record.job_id == "jid"
        assert record.get("changeset_ids") == ["cid"]

    def test_file_not_yet_processed(self) -> None:
        file_location = "s3://test-bucket/dev/ftp_v2/non-existent-file.xml"
        record = pydantic_from_s3_json(
            ProcessedFileRecord, f"{file_location}.loaded.json", ignore_missing=True
        )
        assert record is None
