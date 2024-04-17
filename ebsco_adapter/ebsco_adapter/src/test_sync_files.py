from sync_files import sync_and_list_files
from test_fixtures import FakeS3Client, FakeEbscoFtp
import tempfile
import datetime
from s3_store import S3Store


def test_sync_and_list_files_not_in_s3():
    with tempfile.TemporaryDirectory() as temp_dir:
        s3_bucket = "test_bucket"
        s3_prefix = "test_prefix"

        file_contents = b"test file contents"

        fake_s3_client = FakeS3Client()
        s3_store = S3Store(s3_bucket, fake_s3_client)
        fake_ebsco_ftp = FakeEbscoFtp({
            "ebz-s7451719-20240322-1.xml": file_contents,
        })

        files_synced = sync_and_list_files(temp_dir, s3_prefix, fake_ebsco_ftp, s3_store)
        assert files_synced == {
            '2024-03-22': {
                'batch_name': '2024-03-22',
                'date': datetime.datetime(2024, 3, 22, 0, 0),
                'filename': 'ebz-s7451719-20240322-1.xml',
                'upload_location': 'test_prefix/ebz-s7451719-20240322-1.xml',
                'download_location': f"{temp_dir}/ebz-s7451719-20240322-1.xml",
            }
        }


def test_sync_and_list_files_already_in_s3():
    with tempfile.TemporaryDirectory() as temp_dir:
        s3_bucket = "test_bucket"
        s3_prefix = "test_prefix"

        file_contents = b"test file contents"

        s3_objects = {
            f"{s3_prefix}/ebz-s7451719-20240322-1.xml": {
                "Body": file_contents,
            }
        }

        fake_s3_client = FakeS3Client(s3_objects)
        s3_store = S3Store(s3_bucket, fake_s3_client)
        fake_ebsco_ftp = FakeEbscoFtp({
            "ebz-s7451719-20240322-1.xml": file_contents,
        })

        files_synced = sync_and_list_files(temp_dir, s3_prefix, fake_ebsco_ftp, s3_store)
        # The file should not be downloaded again (no download_location)
        assert files_synced == {
            '2024-03-22': {
                'batch_name': '2024-03-22',
                'date': datetime.datetime(2024, 3, 22, 0, 0),
                'filename': 'ebz-s7451719-20240322-1.xml',
                'upload_location': 'test_prefix/ebz-s7451719-20240322-1.xml',
            }
        }


def test_sync_and_list_files_only_in_s3():
    with tempfile.TemporaryDirectory() as temp_dir:
        s3_bucket = "test_bucket"
        s3_prefix = "test_prefix"

        file_contents = b"test file contents"

        s3_objects = {
            f"{s3_prefix}/ebz-s7451719-20240322-1.xml": {
                "Body": file_contents,
            }
        }

        fake_s3_client = FakeS3Client(s3_objects)
        s3_store = S3Store(s3_bucket, fake_s3_client)
        fake_ebsco_ftp = FakeEbscoFtp()

        files_synced = sync_and_list_files(temp_dir, s3_prefix, fake_ebsco_ftp, s3_store)
        # The file should still appear in listing (no download_location)
        assert files_synced == {
            '2024-03-22': {
                'batch_name': '2024-03-22',
                'date': datetime.datetime(2024, 3, 22, 0, 0),
                'filename': 'ebz-s7451719-20240322-1.xml',
                'upload_location': 'test_prefix/ebz-s7451719-20240322-1.xml',
            }
        }


def test_sync_and_list_files_no_valid():
    with tempfile.TemporaryDirectory() as temp_dir:
        s3_bucket = "test_bucket"
        s3_prefix = "test_prefix"

        file_contents = b"test file contents"

        fake_s3_client = FakeS3Client()
        s3_store = S3Store(s3_bucket, fake_s3_client)
        fake_ebsco_ftp = FakeEbscoFtp({
            "badfilename-1.xml": file_contents,
        })

        files_synced = sync_and_list_files(temp_dir, s3_prefix, fake_ebsco_ftp, s3_store)
        assert files_synced == {}

