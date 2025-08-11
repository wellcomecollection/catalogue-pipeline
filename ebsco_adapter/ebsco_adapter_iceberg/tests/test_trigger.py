import pytest
from unittest.mock import Mock, patch
from datetime import datetime
from ebsco_ftp import EbscoFtp
from steps.trigger import sync_files, validate_ftp_filename, get_most_recent_S3_object


class TestValidateFtpFilename:
    def test_valid_filename(self):
        """Test that valid filenames pass validation"""
        valid_filenames = [
            "ebz-s7451719-20240322-1.xml",
            "ebz-s7451719-20231225-5.xml", 
            "ebz-s7451719-20200101-10.xml"
        ]
        
        for filename in valid_filenames:
            assert validate_ftp_filename(filename) is True
    
    def test_invalid_prefix(self):
        """Test that files with wrong prefix fail validation"""
        invalid_filenames = [
            "abc-s7451719-20240322-1.xml",
            "ebz-wrong-20240322-1.xml", 
            "wrong-s7451719-20240322-1.xml"
        ]
        
        for filename in invalid_filenames:
            assert validate_ftp_filename(filename) is False
    
    def test_invalid_extension(self):
        """Test that files with wrong extension fail validation"""
        invalid_filenames = [
            "ebz-s7451719-20240322-1.txt",
            "ebz-s7451719-20240322-1.csv",
            "ebz-s7451719-20240322-1"
        ]
        
        for filename in invalid_filenames:
            assert validate_ftp_filename(filename) is False
    
    def test_invalid_date_format(self):
        """Test that files with invalid date format fail validation"""
        invalid_filenames = [
            "ebz-s7451719-2024032-1.xml",  # Missing digit
            "ebz-s7451719-20240332-1.xml",  # Invalid day
            "ebz-s7451719-20241301-1.xml",  # Invalid month
            "ebz-s7451719-abc-1.xml"       # Non-numeric date
        ]
        
        for filename in invalid_filenames:
            assert validate_ftp_filename(filename) is False


class TestGetMostRecentS3Object:
    def setup_method(self):
        """Set up test fixtures"""
        self.mock_s3_client = Mock()
        self.bucket = "test-bucket"
        self.prefix = "test-prefix"
    
    def test_get_most_recent_object(self):
        """Test getting the most recent S3 object by date"""
        self.mock_s3_client.list_objects_v2.return_value = {
            'Contents': [
                {'Key': 'test-prefix/ebz-s7451719-20240320-1.xml'},
                {'Key': 'test-prefix/ebz-s7451719-20240325-1.xml'},
                {'Key': 'test-prefix/ebz-s7451719-20240315-1.xml'},
                {'Key': 'test-prefix/ebz-s7451719-20240322-1.xml'}
            ]
        }
        
        result = get_most_recent_S3_object(self.mock_s3_client, self.bucket, self.prefix)
        
        # Should return the file with the highest date (20240325)
        assert result == 'test-prefix/ebz-s7451719-20240325-1.xml'
        self.mock_s3_client.list_objects_v2.assert_called_once_with(
            Bucket=self.bucket, 
            Prefix=self.prefix
        )
    
    def test_no_objects_found(self):
        """Test error when no objects are found in S3"""
        self.mock_s3_client.list_objects_v2.return_value = {}
        
        with pytest.raises(ValueError, match="No objects found in S3"):
            get_most_recent_S3_object(self.mock_s3_client, self.bucket, self.prefix)
    
    def test_empty_contents(self):
        """Test error when Contents is empty"""
        self.mock_s3_client.list_objects_v2.return_value = {'Contents': []}
        
        with pytest.raises(ValueError, match="No objects found in S3"):
            get_most_recent_S3_object(self.mock_s3_client, self.bucket, self.prefix)


class TestSyncFiles:
    def setup_method(self):
        """Set up test fixtures"""
        self.mock_ebsco_ftp = Mock(spec=EbscoFtp)
        self.target_directory = "/tmp/test"
        self.s3_bucket = "test-bucket"
        self.s3_prefix = "test-prefix"
    
    @patch('steps.trigger.get_most_recent_S3_object')
    @patch('steps.trigger.boto3')
    def test_successful_download_and_upload(self, mock_boto3, mock_get_recent_s3):
        """Test successful download of *most recent* file from FTP and upload to S3"""
        # Setup FTP mock
        ftp_files = [ 
            "ebz-s7451719-20240315-1.xml",
            "ebz-s7451719-20240320-1.xml",
            "ebz-s7451719-20240325-1.xml",
            "ebz-s7451719-20240318-1.xml"
        ]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        download_path = "/tmp/test/ebz-s7451719-20240325-1.xml"
        self.mock_ebsco_ftp.download_file.return_value = download_path
        
        # Setup S3 mock - file doesn't exist, then successful upload
        mock_s3_client = Mock()
        mock_boto3.client.return_value = mock_s3_client
        mock_s3_client.head_object.side_effect = Exception("NoSuchKey")
        
        # Mock the get_most_recent_S3_object function
        mock_get_recent_s3.return_value = "test-prefix/ebz-s7451719-20240325-1.xml"
        
        result = sync_files(
            ebsco_ftp=self.mock_ebsco_ftp,
            target_directory=self.target_directory,
            s3_bucket=self.s3_bucket,
            s3_prefix=self.s3_prefix
        )
        
        # Verify calls
        self.mock_ebsco_ftp.list_files.assert_called_once_with(validate_ftp_filename)
        self.mock_ebsco_ftp.download_file.assert_called_once_with("ebz-s7451719-20240325-1.xml", self.target_directory)
        mock_s3_client.upload_file.assert_called_once_with(
            download_path, 
            self.s3_bucket, 
            "test-prefix/ebz-s7451719-20240325-1.xml"
        )
        mock_get_recent_s3.assert_called_once_with(mock_s3_client, self.s3_bucket, self.s3_prefix)
        
        # Verify result
        expected_result = f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240325-1.xml"
        assert result == expected_result


    def test_no_xml_files_found(self):
        """Test ValueError when no valid XML files are found on FTP"""
        self.mock_ebsco_ftp.list_files.return_value = []
        
        with pytest.raises(ValueError, match="No XML files found on FTP server"):
            sync_files(
                ebsco_ftp=self.mock_ebsco_ftp,
                target_directory=self.target_directory,
                s3_bucket=self.s3_bucket,
                s3_prefix=self.s3_prefix
            )

    def test_file_already_exists_in_s3(self):
        """Test early return when file already exists in S3"""
        # Setup FTP mock
        ftp_files = [
            "ebz-s7451719-20240320-1.xml", 
            "ebz-s7451719-20240322-1.xml", 
            "ebz-s7451719-20240321-1.xml"
        ]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        
        with patch('steps.trigger.boto3') as mock_boto3:
            # Setup S3 mock - file exists
            mock_s3_client = Mock()
            mock_boto3.client.return_value = mock_s3_client
            mock_s3_client.head_object.return_value = {}  # File exists
            
            result = sync_files(
                ebsco_ftp=self.mock_ebsco_ftp,
                target_directory=self.target_directory,
                s3_bucket=self.s3_bucket,
                s3_prefix=self.s3_prefix
            )
            
            # Should return S3 URL without downloading or uploading
            expected_result = f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240322-1.xml"
            assert result == expected_result
            self.mock_ebsco_ftp.download_file.assert_not_called()
            mock_s3_client.upload_file.assert_not_called()

    @patch('steps.trigger.boto3')
    def test_ftp_download_failure(self, mock_boto3):
        """Test RuntimeError when FTP download fails"""
        # Setup FTP mock with download failure
        ftp_files = ["ebz-s7451719-20240322-1.xml"]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        self.mock_ebsco_ftp.download_file.side_effect = Exception("FTP connection failed")
        
        # Setup S3 mock - file doesn't exist (so we proceed to download)
        mock_s3_client = Mock()
        mock_boto3.client.return_value = mock_s3_client
        mock_s3_client.head_object.side_effect = Exception("NoSuchKey")
        
        with pytest.raises(RuntimeError, match="Failed to download ebz-s7451719-20240322-1.xml from FTP"):
            sync_files(
                ebsco_ftp=self.mock_ebsco_ftp,
                target_directory=self.target_directory,
                s3_bucket=self.s3_bucket,
                s3_prefix=self.s3_prefix
            )

    @patch('steps.trigger.boto3')
    def test_s3_upload_failure(self, mock_boto3):
        """Test RuntimeError when S3 upload fails"""
        # Setup FTP mock
        ftp_files = ["ebz-s7451719-20240322-1.xml"]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        download_path = "/tmp/test/ebz-s7451719-20240322-1.xml"
        self.mock_ebsco_ftp.download_file.return_value = download_path
        
        # Setup S3 mock - file doesn't exist, upload fails
        mock_s3_client = Mock()
        mock_boto3.client.return_value = mock_s3_client
        mock_s3_client.head_object.side_effect = Exception("NoSuchKey")
        mock_s3_client.upload_file.side_effect = Exception("S3 permission denied")
        
        with pytest.raises(RuntimeError, match="Failed to upload ebz-s7451719-20240322-1.xml to S3"):
            sync_files(
                ebsco_ftp=self.mock_ebsco_ftp,
                target_directory=self.target_directory,
                s3_bucket=self.s3_bucket,
                s3_prefix=self.s3_prefix
            )

    @patch('steps.trigger.get_most_recent_S3_object')
    @patch('steps.trigger.boto3')
    def test_forward_most_recent_s3_file(self, mock_boto3, mock_get_recent_s3):
        """Test that we notify downstream of the most recent S3 file, even if it's not the one we just uploaded"""
        ftp_files = [
            "ebz-s7451719-20240415-1.xml",
            "ebz-s7451719-20240420-1.xml",
            "ebz-s7451719-20240425-1.xml",
            "ebz-s7451719-20240418-1.xml"
        ]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        download_path = "/tmp/test/ebz-s7451719-20240425-1.xml"
        self.mock_ebsco_ftp.download_file.return_value = download_path

        # Setup S3 mock - file doesn't exist, then successful upload
        mock_s3_client = Mock()
        mock_boto3.client.return_value = mock_s3_client
        mock_s3_client.head_object.side_effect = Exception("NoSuchKey")

        # There's actually a more recent file in S3
        mock_get_recent_s3.return_value = "test-prefix/ebz-s7451719-20240428-1.xml"

        result = sync_files(
            ebsco_ftp=self.mock_ebsco_ftp,
            target_directory=self.target_directory,
            s3_bucket=self.s3_bucket,
            s3_prefix=self.s3_prefix
        )

        # Should select the newest file in s3 (20240428)
        expected_result = f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240428-1.xml"
        assert result == expected_result

    @patch('steps.trigger.get_most_recent_S3_object')
    @patch('steps.trigger.boto3')
    def test_s3_head_object_general_exception(self, mock_boto3, mock_get_recent_s3):
        """Test that general S3 exceptions are handled and processing continues"""
        # Setup FTP mock
        ftp_files = ["ebz-s7451719-20240322-1.xml"]
        self.mock_ebsco_ftp.list_files.return_value = ftp_files
        download_path = "/tmp/test/ebz-s7451719-20240322-1.xml"
        self.mock_ebsco_ftp.download_file.return_value = download_path
        
        # Setup S3 mock - head_object fails with general exception, upload succeeds
        mock_s3_client = Mock()
        mock_boto3.client.return_value = mock_s3_client
        mock_s3_client.head_object.side_effect = Exception("Network timeout")
        
        # Mock the get_most_recent_S3_object function
        mock_get_recent_s3.return_value = "test-prefix/ebz-s7451719-20240322-1.xml"
        
        result = sync_files(
            ebsco_ftp=self.mock_ebsco_ftp,
            target_directory=self.target_directory,
            s3_bucket=self.s3_bucket,
            s3_prefix=self.s3_prefix
        )
        
        # Should proceed with download and upload despite S3 check failure
        self.mock_ebsco_ftp.download_file.assert_called_once()
        mock_s3_client.upload_file.assert_called_once()
        mock_get_recent_s3.assert_called_once()
        
        expected_result = f"s3://{self.s3_bucket}/test-prefix/ebz-s7451719-20240322-1.xml"
        assert result == expected_result