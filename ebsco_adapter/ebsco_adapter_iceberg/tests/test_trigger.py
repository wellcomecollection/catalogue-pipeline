from typing import cast
from unittest.mock import Mock, patch
from unittest.mock import Mock as _Mock

import pytest

from ebsco_ftp import EbscoFtp
from models.step_events import EbscoAdapterLoaderEvent
from steps.trigger import (
    EventBridgeScheduledEvent,
    get_most_recent_valid_file,
    sync_files,
)


class TestMostRecentValidFile:
    def test_valid(self) -> None:
        files = [
            "ebz-s7451719-20240322-1.xml",
            "ebz-s7451719-20231225-5.xml",
            "ebz-s7451719-20200101-10.xml",
        ]
        assert get_most_recent_valid_file(files) == "ebz-s7451719-20240322-1.xml"

    def test_invalid_sets(self) -> None:
        assert get_most_recent_valid_file(["abc-s7451719-20240322-1.xml"]) is None
        assert get_most_recent_valid_file(["ebz-s7451719-20240322-1.txt"]) is None
        with pytest.raises(ValueError):
            get_most_recent_valid_file(["ebz-s7451719-20240332-1.xml"])  # bad day


class TestSyncFiles:
    def setup_method(self) -> None:
        self.mock_ebsco_ftp = Mock(spec=EbscoFtp)
        self.target_directory = "/tmp/test"
        self.s3_bucket = "test-bucket"
        self.s3_prefix = "test-prefix"
        self.mock_list_s3_keys = patch("steps.trigger.list_s3_keys").start()

    def teardown_method(self) -> None:
        self.mock_list_s3_keys.stop()

    def test_successful_download_and_upload(self) -> None:
        ftp_files = [
            "ebz-s7451719-20240315-1.xml",
            "ebz-s7451719-20240320-1.xml",
            "ebz-s7451719-20240325-1.xml",
        ]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        download_path = "/tmp/test/ebz-s7451719-20240325-1.xml"
        self.mock_ebsco_ftp.download_file.return_value = download_path
        # create dummy downloaded file
        import os

        os.makedirs(os.path.dirname(download_path), exist_ok=True)
        with open(download_path, "wb") as f:
            f.write(b"<xml></xml>")
        # Start with S3 missing the file -> list returns some older key
        self.mock_list_s3_keys.side_effect = [
            ["test-prefix/ebz-s7451719-20240320-1.xml"],  # initial existence check list
            [  # refreshed after upload
                "test-prefix/ebz-s7451719-20240315-1.xml",
                "test-prefix/ebz-s7451719-20240320-1.xml",
                "test-prefix/ebz-s7451719-20240325-1.xml",
            ],
        ]
        result = sync_files(
            ebsco_ftp=self.mock_ebsco_ftp,
            target_directory=self.target_directory,
            s3_bucket=self.s3_bucket,
            s3_prefix=self.s3_prefix,
        )
        self.mock_ebsco_ftp.download_file.assert_called_once_with(
            "ebz-s7451719-20240325-1.xml", self.target_directory
        )
        assert (
            result == f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240325-1.xml"
        )

    def test_file_already_exists_in_s3(self) -> None:
        ftp_files = [
            "ebz-s7451719-20240320-1.xml",
            "ebz-s7451719-20240322-1.xml",
        ]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        # First list shows file already present -> no download
        self.mock_list_s3_keys.return_value = [
            "test-prefix/ebz-s7451719-20240322-1.xml"
        ]
        result = sync_files(
            ebsco_ftp=self.mock_ebsco_ftp,
            target_directory=self.target_directory,
            s3_bucket=self.s3_bucket,
            s3_prefix=self.s3_prefix,
        )
        assert (
            result == f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240322-1.xml"
        )
        self.mock_ebsco_ftp.download_file.assert_not_called()

    def test_no_xml_files_found(self) -> None:
        self.mock_ebsco_ftp.list_files.return_value = []
        with pytest.raises(ValueError):
            sync_files(
                ebsco_ftp=self.mock_ebsco_ftp,
                target_directory=self.target_directory,
                s3_bucket=self.s3_bucket,
                s3_prefix=self.s3_prefix,
            )

    def test_download_failure(self) -> None:
        self.mock_ebsco_ftp.list_files.return_value = ["ebz-s7451719-20240322-1.xml"]
        self.mock_ebsco_ftp.download_file.side_effect = Exception("fail")
        with pytest.raises(RuntimeError):
            sync_files(
                ebsco_ftp=self.mock_ebsco_ftp,
                target_directory=self.target_directory,
                s3_bucket=self.s3_bucket,
                s3_prefix=self.s3_prefix,
            )

    def test_upload_failure(self) -> None:
        self.mock_ebsco_ftp.list_files.return_value = ["ebz-s7451719-20240322-1.xml"]
        self.mock_ebsco_ftp.download_file.return_value = "/tmp/test/file.xml"
        # ensure dummy file exists so failure is due to smart_open, not missing file
        import os

        os.makedirs("/tmp/test", exist_ok=True)
        with open("/tmp/test/file.xml", "wb") as f:
            f.write(b"content")
        # First list shows file absent, second list call should not occur due to upload failure
        self.mock_list_s3_keys.side_effect = [
            [],  # before upload
        ]

        # Patch smart_open to raise on write
        with patch(
            "steps.trigger.smart_open.open", side_effect=OSError("Denied")
        ), pytest.raises(RuntimeError, match="Denied"):
            sync_files(
                ebsco_ftp=self.mock_ebsco_ftp,
                target_directory=self.target_directory,
                s3_bucket=self.s3_bucket,
                s3_prefix=self.s3_prefix,
            )

    def test_forward_most_recent_s3_file(self) -> None:
        ftp_files = [
            "ebz-s7451719-20240420-1.xml",
            "ebz-s7451719-20240425-1.xml",
        ]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        self.mock_ebsco_ftp.download_file.return_value = (
            "/tmp/test/ebz-s7451719-20240425-1.xml"
        )
        # S3 already has a newer file than any on FTP; list shows newer first
        self.mock_list_s3_keys.return_value = [
            "test-prefix/ebz-s7451719-20240428-1.xml",
            "test-prefix/ebz-s7451719-20240425-1.xml",
        ]
        result = sync_files(
            ebsco_ftp=self.mock_ebsco_ftp,
            target_directory=self.target_directory,
            s3_bucket=self.s3_bucket,
            s3_prefix=self.s3_prefix,
        )
        assert (
            result == f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240428-1.xml"
        )


@patch("steps.trigger.handler")
def test_lambda_handler_eventbridge_conversion(mock_handler) -> None:  # type: ignore
    scheduled_time = "2025-08-22T12:34:00Z"
    expected_job_id = "20250822T1234"
    mock_handler.return_value = EbscoAdapterLoaderEvent(
        job_id=expected_job_id, file_location="s3://test/file.xml"
    )
    from steps.trigger import lambda_handler

    result = lambda_handler(EventBridgeScheduledEvent(time=scheduled_time), None)
    handler_mock = cast(_Mock, mock_handler)
    args_tuple = handler_mock.call_args[0]
    internal_event = args_tuple[0]
    assert internal_event.job_id == expected_job_id
    assert result["job_id"] == expected_job_id
