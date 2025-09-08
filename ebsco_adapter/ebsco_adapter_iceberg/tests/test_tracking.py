import json
from unittest.mock import Mock, patch

from utils.tracking import (
    ProcessedFileRecord,
    is_file_already_processed,
    record_processed_file,
)


class TestRecordProcessedFile:
    def setup_method(self) -> None:
        """Setup method run before each test."""
        self.mock_smart_open = patch("utils.tracking.smart_open.open").start()
        self.mock_file = Mock()
        self.mock_smart_open.return_value.__enter__.return_value = self.mock_file

    def teardown_method(self) -> None:
        """Teardown method run after each test."""
        patch("utils.tracking.smart_open.open").stop()

    def test_record_processed_file(self) -> None:
        job_id = "test-job-id"
        file_location = "s3://s3-bucket/is-a/file.xml"
        changeset_id = "I have changed"
        record = record_processed_file(
            job_id, file_location, changeset_id, step="loaded"
        )

        assert isinstance(record, ProcessedFileRecord)
        assert record.job_id == job_id
        assert record.changeset_id == changeset_id
        assert record.step == "loaded"

        # Verify smart_open was called with the correct S3 URI
        self.mock_smart_open.assert_called_once_with(
            "s3://s3-bucket/is-a/file.xml.loaded.json", "w", encoding="utf-8"
        )

        # Verify the JSON was written correctly
        # Order of keys in JSON dump isn't guaranteed; compare via parsed dict
        written_args = self.mock_file.write.call_args[0][0]
        assert json.loads(written_args) == {
            "job_id": job_id,
            "changeset_id": changeset_id,
            "step": "loaded",
        }


class TestIsFileAlreadyProcessed:
    def setup_method(self) -> None:
        self.mock_boto3_client = patch("utils.tracking.boto3.client").start()
        self.mock_s3 = Mock()
        self.mock_boto3_client.return_value = self.mock_s3

    def teardown_method(self) -> None:
        patch("utils.tracking.boto3.client").stop()

    def test_file_already_processed(self) -> None:
        bucket = "test-bucket"
        key = "dev/ftp_v2/existing-file.xml"
        record_dict = {"job_id": "jid", "changeset_id": "cid", "step": "loaded"}
        body_mock = Mock()
        body_mock.read.return_value = json.dumps(record_dict).encode("utf-8")
        self.mock_s3.get_object.return_value = {"Body": body_mock}

        record = is_file_already_processed(bucket, key, step="loaded")
        assert isinstance(record, ProcessedFileRecord)
        assert record.job_id == "jid"
        assert record.changeset_id == "cid"

    def test_file_not_yet_processed(self) -> None:
        bucket = "test-bucket"
        key = "dev/ftp_v2/non-existent-file.xml"
        self.mock_s3.get_object.side_effect = Exception()
        record = is_file_already_processed(bucket, key, step="loaded")
        assert record is None
