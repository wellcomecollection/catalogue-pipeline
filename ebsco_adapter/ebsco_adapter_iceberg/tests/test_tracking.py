import json
from unittest.mock import Mock, patch

from utils.tracking import (
    is_file_already_processed,
    record_processed_file,
)


class TestRecordProcessedFile:
    def setup_method(self) -> None:
        """Setup method run before each test."""
        self.mock_boto3_client = patch("utils.tracking.boto3.client").start()
        self.mock_s3 = Mock()
        self.mock_boto3_client.return_value = self.mock_s3

    def teardown_method(self) -> None:
        """Teardown method run after each test."""
        patch("utils.tracking.boto3.client").stop()

    def test_record_processed_file(self) -> None:
        job_id = "test-job-id"
        file_location = "right/there-in-the/s3-bucket/is-a/file.xml"
        changeset_id = "I have changed"

        record = record_processed_file(job_id, file_location, changeset_id)

        assert record == {"job_id": job_id, "changeset_id": changeset_id}

        self.mock_s3.put_object.assert_called_once_with(
            Bucket="s3-bucket",
            Key="is-a/file.xml.loaded.json",
            Body=json.dumps(record),
            ContentType="application/json",
        )


class TestIsFileAlreadyProcessed:
    def setup_method(self) -> None:
        """Setup method run before each test."""
        self.mock_boto3_client = patch("utils.tracking.boto3.client").start()
        self.mock_s3 = Mock()
        self.mock_boto3_client.return_value = self.mock_s3

    def teardown_method(self) -> None:
        """Teardown method run after each test."""
        patch("utils.tracking.boto3.client").stop()

    def test_file_already_processed(self) -> None:
        bucket = "test-bucket"
        key = "dev/ftp_v2/existing-file.xml.loaded.json"

        self.mock_s3.head_object.return_value = {}
        is_it = is_file_already_processed(bucket, key)

        assert is_it is True

    def test_file_not_yet_processed(self) -> None:
        bucket = "test-bucket"
        key = "dev/ftp_v2/non-existent-file.xml.loaded.json"

        self.mock_s3.head_object.side_effect = Exception("NoSuchKey")
        is_it = is_file_already_processed(bucket, key)

        assert is_it is False
