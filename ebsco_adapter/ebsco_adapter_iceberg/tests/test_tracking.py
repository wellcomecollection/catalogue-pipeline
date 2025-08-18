from unittest.mock import Mock, patch

from botocore.exceptions import ClientError  # type: ignore

from utils.tracking import (
    _get_tracking_file_content,
    _get_tracking_info,
    is_file_already_processed,
    record_processed_file,
)


class TestGetTrackingInfo:
    def test_get_tracking_info(self) -> None:
        s3_uri = "s3://test-bucket/dev/ftp_v2/ebz-s7451719-20240322-1.xml"
        result = _get_tracking_info(s3_uri)

        expected = {
            "bucket": "test-bucket",
            "key": "dev/ftp_v2/processed_files.txt",
            "filename": "ebz-s7451719-20240322-1.xml",
        }
        assert result == expected

    def test_get_tracking_info_nested_prefix(self) -> None:
        s3_uri = "s3://test-bucket/very/deep/nested/path/ebz-s7451719-20240322-1.xml"
        result = _get_tracking_info(s3_uri)

        expected = {
            "bucket": "test-bucket",
            "key": "very/deep/nested/path/processed_files.txt",
            "filename": "ebz-s7451719-20240322-1.xml",
        }
        assert result == expected


class TestGetTrackingFileContent:
    @patch("utils.tracking.boto3.client")
    def test_get_tracking_file_content_file_exists(self, mock_boto3_client) -> None:  # type: ignore
        mock_s3 = Mock()
        mock_boto3_client.return_value = mock_s3

        mock_response = {"Body": Mock()}
        mock_response["Body"].read.return_value = b"file1.xml\nfile2.xml\nfile3.xml"
        mock_s3.get_object.return_value = mock_response

        s3_uri = "s3://test-bucket/dev/ftp_v2/test-file.xml"

        tracking_info, content = _get_tracking_file_content(s3_uri)

        assert content == "file1.xml\nfile2.xml\nfile3.xml"
        assert tracking_info["bucket"] == "test-bucket"
        assert tracking_info["key"] == "dev/ftp_v2/processed_files.txt"
        assert tracking_info["filename"] == "test-file.xml"
        mock_s3.get_object.assert_called_once_with(
            Bucket="test-bucket", Key="dev/ftp_v2/processed_files.txt"
        )

    @patch("utils.tracking.boto3.client")
    def test_get_tracking_file_content_file_not_exists(self, mock_boto3_client) -> None:  # type: ignore
        mock_s3 = Mock()
        mock_boto3_client.return_value = mock_s3

        # Simulate NoSuchKey exception
        mock_s3.exceptions.NoSuchKey = ClientError
        mock_s3.get_object.side_effect = ClientError(
            error_response={"Error": {"Code": "NoSuchKey"}}, operation_name="GetObject"
        )

        s3_uri = "s3://test-bucket/dev/ftp_v2/test-file.xml"

        tracking_info, content = _get_tracking_file_content(s3_uri)

        assert content is None
        assert tracking_info["bucket"] == "test-bucket"
        assert tracking_info["key"] == "dev/ftp_v2/processed_files.txt"
        assert tracking_info["filename"] == "test-file.xml"


class TestRecordProcessedFile:
    def setup_method(self) -> None:
        """Setup method run before each test."""
        self.mock_boto3_client = patch("utils.tracking.boto3.client").start()
        self.mock_get_content = patch(
            "utils.tracking._get_tracking_file_content"
        ).start()
        self.mock_s3 = Mock()
        self.mock_boto3_client.return_value = self.mock_s3

    def teardown_method(self) -> None:
        """Teardown method run after each test."""
        patch("utils.tracking.boto3.client").stop()
        patch("utils.tracking._get_tracking_file_content").stop()

    def test_record_processed_file_new_tracking_file(self) -> None:
        tracking_info = {
            "bucket": "test-bucket",
            "key": "dev/ftp_v2/processed_files.txt",
            "filename": "new-file.xml",
        }
        self.mock_get_content.return_value = (tracking_info, None)

        s3_uri = "s3://test-bucket/dev/ftp_v2/new-file.xml"

        record_processed_file(s3_uri)

        self.mock_s3.put_object.assert_called_once_with(
            Bucket="test-bucket",
            Key="dev/ftp_v2/processed_files.txt",
            Body=b"new-file.xml",
            ContentType="text/plain",
        )

    def test_record_processed_file_append_to_existing(self) -> None:
        tracking_info = {
            "bucket": "test-bucket",
            "key": "dev/ftp_v2/processed_files.txt",
            "filename": "new-file.xml",
        }
        existing_content = "file1.xml\nfile2.xml"
        self.mock_get_content.return_value = (tracking_info, existing_content)

        s3_uri = "s3://test-bucket/dev/ftp_v2/new-file.xml"

        record_processed_file(s3_uri)

        expected_content = "file1.xml\nfile2.xml\nnew-file.xml"
        self.mock_s3.put_object.assert_called_once_with(
            Bucket="test-bucket",
            Key="dev/ftp_v2/processed_files.txt",
            Body=expected_content.encode("utf-8"),
            ContentType="text/plain",
        )


class TestIsFileAlreadyProcessed:
    def setup_method(self) -> None:
        """Setup method run before each test."""
        self.mock_get_content = patch(
            "utils.tracking._get_tracking_file_content"
        ).start()

    def teardown_method(self) -> None:
        """Teardown method run after each test."""
        patch("utils.tracking._get_tracking_file_content").stop()

    def test_is_file_already_processed_file_found(self) -> None:
        tracking_info = {
            "bucket": "test-bucket",
            "key": "dev/ftp_v2/processed_files.txt",
            "filename": "existing-file.xml",
        }
        existing_content = "file1.xml\nexisting-file.xml\nfile3.xml"
        self.mock_get_content.return_value = (tracking_info, existing_content)

        s3_uri = "s3://test-bucket/dev/ftp_v2/existing-file.xml"

        result = is_file_already_processed(s3_uri)

        assert result is True

    def test_is_file_already_processed_file_not_found(self) -> None:
        tracking_info = {
            "bucket": "test-bucket",
            "key": "dev/ftp_v2/processed_files.txt",
            "filename": "missing-file.xml",
        }
        existing_content = "file1.xml\nfile2.xml\nfile3.xml"
        self.mock_get_content.return_value = (tracking_info, existing_content)

        s3_uri = "s3://test-bucket/dev/ftp_v2/missing-file.xml"

        result = is_file_already_processed(s3_uri)

        assert result is False

    def test_is_file_already_processed_no_tracking_file(self) -> None:
        tracking_info = {
            "bucket": "test-bucket",
            "key": "dev/ftp_v2/processed_files.txt",
            "filename": "any-file.xml",
        }
        self.mock_get_content.return_value = (tracking_info, None)

        s3_uri = "s3://test-bucket/dev/ftp_v2/any-file.xml"

        result = is_file_already_processed(s3_uri)

        assert result is False
