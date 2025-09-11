import tempfile
import os

from extract_marc import extract_marc_records
from test_fixtures import FakeS3Client

from s3_store import S3Store


xml_s3_prefix = "xml"
ftp_s3_prefix = "ftp"
batch_name = "2024-03-22"
fixture_file_name = "ebz-s7451719-20240328-1.xml"

current_dir = os.path.dirname(os.path.realpath(__file__))
fixtures_file = os.path.join(current_dir, "fixtures", fixture_file_name)
expected_results = {
    "2024-03-22": {
        "ebs9579e": {
            "s3_key": f"xml/{batch_name}/ebs9579e.xml",
            "sha256": "4c4a7fb13bf8b7beda96bbe75358eb882a6130592b29f6149a308d82e0356d22",
        },
        "ebs29555e": {
            "s3_key": f"xml/{batch_name}/ebs29555e.xml",
            "sha256": "cb719218d3435395a2fa948088bf2c0fe86748dddfb5a2e8e2bbb199f6cf48dd",
        },
    }
}

available_files = {
    "2024-03-22": {
        "batch_name": batch_name,
        "date": batch_name,
        "filename": fixture_file_name,
        "upload_location": f"{ftp_s3_prefix}/{fixture_file_name}",
        "download_location": fixtures_file,
    }
}

with open(fixtures_file, "rb") as f:
    file_contents = f.read()


def test_extract_marc_records_reads_from_download_location():
    with tempfile.TemporaryDirectory() as temp_dir:
        # Don't add objects to S3, as we want to test that the download_location is used
        s3_objects = {}

        fake_s3_client = FakeS3Client(s3_objects)
        s3_store = S3Store("test_bucket", fake_s3_client)

        results = extract_marc_records(
            available_files, xml_s3_prefix, temp_dir, s3_store
        )
        assert results == expected_results, (
            "Unexpected results from extract_marc_records"
        )


def test_extract_marc_records_reads_from_upload_location():
    with tempfile.TemporaryDirectory() as temp_dir:
        s3_objects = {
            f"{ftp_s3_prefix}/{fixture_file_name}": {
                "Body": file_contents,
            }
        }

        fake_s3_client = FakeS3Client(s3_objects)
        s3_store = S3Store("test_bucket", fake_s3_client)

        results = extract_marc_records(
            available_files, xml_s3_prefix, temp_dir, s3_store
        )
        assert results == expected_results, (
            "Unexpected results from extract_marc_records"
        )
