from unittest.mock import Mock, patch

import pytest

from ebsco_ftp import EbscoFtp
from steps.trigger import sync_files, get_most_recent_valid_file


class TestMostRecentValidFile:
    def test_valid_filenames(self) -> None:
        """Test that the most recent valid file is returned"""
        valid_filenames = [
            "ebz-s7451719-20240322-1.xml",
            "ebz-s7451719-20231225-5.xml",
            "ebz-s7451719-20200101-10.xml",
        ]

        assert get_most_recent_valid_file(valid_filenames) == "ebz-s7451719-20240322-1.xml"

    def test_invalid_prefix(self) -> None:
        """Test that files with wrong prefix fail validation"""
        invalid_filenames = [
            "abc-s7451719-20240322-1.xml",
            "ebz-wrong-20240322-1.xml",
            "wrong-s7451719-20240322-1.xml",
        ]

        assert get_most_recent_valid_file(invalid_filenames) is None

    def test_invalid_extension(self) -> None:
        """Test that files with wrong extension fail validation"""
        invalid_filenames = [
            "ebz-s7451719-20240322-1.txt",
            "ebz-s7451719-20240322-1.csv",
            "ebz-s7451719-20240322-1",
        ]

        assert get_most_recent_valid_file(invalid_filenames) is None

    def test_invalid_date_format(self) -> None:
        """Test that files with invalid date format fail validation"""
        invalid_filenames = [
            "ebz-s7451719-2024032-1.xml",  # Missing digit
            "ebz-s7451719-20240332-1.xml",  # Invalid day
            "ebz-s7451719-20241301-1.xml",  # Invalid month
            "ebz-s7451719-abc-1.xml",  # Non-numeric date
        ]

        assert get_most_recent_valid_file(invalid_filenames) is None


class TestSyncFiles:
    def setup_method(self) -> None:
        """Set up test fixtures"""
        self.mock_ebsco_ftp = Mock(spec=EbscoFtp)
        self.target_directory = "/tmp/test"
        self.s3_bucket = "test-bucket"
        self.s3_prefix = "test-prefix"
        self.mock_list_s3_keys = patch("steps.trigger.list_s3_keys").start()
        
        # Mock boto3 for the S3 client used in sync_files
        self.mock_boto3 = patch("steps.trigger.boto3").start()
        self.mock_s3_client = Mock()
        self.mock_boto3.client.return_value = self.mock_s3_client

    def teardown_method(self) -> None:
        """Clean up patches"""
        self.mock_list_s3_keys.stop()
        self.mock_boto3.stop()

    def test_successful_download_and_upload(self) -> None:
        """Test successful download of *most recent* file from FTP and upload to S3"""
        # Setup FTP mock
        ftp_files = [
            "ebz-s7451719-20240315-1.xml",
            "ebz-s7451719-20240320-1.xml",
            "ebz-s7451719-20240325-1.xml",
            "ebz-s7451719-20240318-1.xml",
        ]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        download_path = "/tmp/test/ebz-s7451719-20240325-1.xml"
        self.mock_ebsco_ftp.download_file.return_value = download_path

        # Setup S3 mock - file doesn't exist, then successful upload
        self.mock_s3_client.head_object.side_effect = Exception("NoSuchKey")

        # Mock the list_s3_keys function
        self.mock_list_s3_keys.return_value = [
            "test-prefix/ebz-s7451719-20240325-1.xml",
            "test-prefix/ebz-s7451719-20220325-1.xml"
        ]
            
        result = sync_files(
            ebsco_ftp=self.mock_ebsco_ftp,
            target_directory=self.target_directory,
            s3_bucket=self.s3_bucket,
            s3_prefix=self.s3_prefix,
        )

        # Verify calls
        self.mock_ebsco_ftp.list_files.assert_called_once()
        self.mock_ebsco_ftp.download_file.assert_called_once_with(
            "ebz-s7451719-20240325-1.xml", self.target_directory
        )
        self.mock_s3_client.upload_file.assert_called_once_with(
            download_path, self.s3_bucket, "test-prefix/ebz-s7451719-20240325-1.xml"
        )

        # Verify result
        expected_result = (
            f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240325-1.xml"
        )
        assert result == expected_result

    def test_no_xml_files_found(self) -> None:
        """Test ValueError when no XML files are found on FTP"""
        self.mock_ebsco_ftp.list_files.return_value = []

        with pytest.raises(ValueError, match="No valid files found on FTP server"):
            sync_files(
                ebsco_ftp=self.mock_ebsco_ftp,
                target_directory=self.target_directory,
                s3_bucket=self.s3_bucket,
                s3_prefix=self.s3_prefix,
            )

    def test_file_already_exists_in_s3(self) -> None:
        """Test early return when file already exists in S3"""
        # Setup FTP mock
        ftp_files = [
            "ebz-s7451719-20240320-1.xml",
            "ebz-s7451719-20240322-1.xml",
            "ebz-s7451719-20240321-1.xml",
        ]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files

        # Setup S3 mock - file exists
        self.mock_s3_client.head_object.return_value = {}  # File exists

        self.mock_list_s3_keys.return_value = [
            "test-prefix/ebz-s7451719-20240322-1.xml"
        ]

        result = sync_files(
            ebsco_ftp=self.mock_ebsco_ftp,
            target_directory=self.target_directory,
            s3_bucket=self.s3_bucket,
            s3_prefix=self.s3_prefix,
        )

        # Should return S3 URL without downloading or uploading
        expected_result = (
            f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240322-1.xml"
        )
        assert result == expected_result
        self.mock_ebsco_ftp.download_file.assert_not_called()
        self.mock_s3_client.upload_file.assert_not_called()

    def test_ftp_download_failure(self) -> None:
        """Test RuntimeError when FTP download fails"""
        # Setup FTP mock with download failure
        ftp_files = ["ebz-s7451719-20240322-1.xml"]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        self.mock_ebsco_ftp.download_file.side_effect = Exception(
            "FTP connection failed"
        )

        # Setup S3 mock - file doesn't exist (so we proceed to download)
        self.mock_s3_client.head_object.side_effect = Exception("NoSuchKey")

        with pytest.raises(
            RuntimeError,
            match="Failed to download ebz-s7451719-20240322-1.xml from FTP",
        ):
            sync_files(
                ebsco_ftp=self.mock_ebsco_ftp,
                target_directory=self.target_directory,
                s3_bucket=self.s3_bucket,
                s3_prefix=self.s3_prefix,
            )

    def test_s3_upload_failure(self) -> None:
        """Test RuntimeError when S3 upload fails"""
        # Setup FTP mock
        ftp_files = ["ebz-s7451719-20240322-1.xml"]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        download_path = "/tmp/test/ebz-s7451719-20240322-1.xml"
        self.mock_ebsco_ftp.download_file.return_value = download_path

        # Setup S3 mock - file doesn't exist, upload fails
        self.mock_s3_client.head_object.side_effect = Exception("NoSuchKey")
        self.mock_s3_client.upload_file.side_effect = Exception("S3 permission denied")

        with pytest.raises(
            RuntimeError, match="Failed to upload ebz-s7451719-20240322-1.xml to S3"
        ):
            sync_files(
                ebsco_ftp=self.mock_ebsco_ftp,
                target_directory=self.target_directory,
                s3_bucket=self.s3_bucket,
                s3_prefix=self.s3_prefix,
            )

    def test_forward_most_recent_s3_file(self) -> None:
        """Test that we notify downstream of the most recent S3 file, even if it's not the one we just uploaded"""
        ftp_files = [
            "ebz-s7451719-20240415-1.xml",
            "ebz-s7451719-20240420-1.xml",
            "ebz-s7451719-20240425-1.xml",
            "ebz-s7451719-20240418-1.xml",
        ]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        download_path = "/tmp/test/ebz-s7451719-20240425-1.xml"
        self.mock_ebsco_ftp.download_file.return_value = download_path

        # Setup S3 mock - file doesn't exist, then successful upload
        self.mock_s3_client.head_object.side_effect = Exception("NoSuchKey")

        # There's actually a more recent file in S3
        self.mock_list_s3_keys.return_value = (
            "test-prefix/ebz-s7451719-20240428-1.xml",
            "test-prefix/ebz-s7451719-20240420-1.xml"
        )

        result = sync_files(
            ebsco_ftp=self.mock_ebsco_ftp,
            target_directory=self.target_directory,
            s3_bucket=self.s3_bucket,
            s3_prefix=self.s3_prefix,
        )

        # Should select the newest file in s3 (20240428)
        expected_result = (
            f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240428-1.xml"
        )
        assert result == expected_result

    def test_s3_head_object_general_exception(self) -> None:
        """Test that general S3 exceptions are handled and processing continues"""
        # Setup FTP mock
        ftp_files = ["ebz-s7451719-20240322-1.xml"]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        download_path = "/tmp/test/ebz-s7451719-20240322-1.xml"
        self.mock_ebsco_ftp.download_file.return_value = download_path

        # Setup S3 mock - head_object fails with general exception, upload succeeds
        self.mock_s3_client.head_object.side_effect = Exception("Network timeout")

        # Mock the get_most_recent_S3_object function
        self.mock_list_s3_keys.return_value = [
            "test-prefix/ebz-s7451719-20240322-1.xml"
        ]
            
        result = sync_files(
            ebsco_ftp=self.mock_ebsco_ftp,
            target_directory=self.target_directory,
            s3_bucket=self.s3_bucket,
            s3_prefix=self.s3_prefix,
        )

        # Should proceed with download and upload despite S3 check failure
        self.mock_ebsco_ftp.download_file.assert_called_once()
        self.mock_s3_client.upload_file.assert_called_once()
        self.mock_list_s3_keys.assert_called_once()

        expected_result = (
            f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240322-1.xml"
        )
        assert result == expected_result
