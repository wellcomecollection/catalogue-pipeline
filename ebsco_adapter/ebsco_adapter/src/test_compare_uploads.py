from compare_uploads import compare_uploads
from extract_marc import extract_marc_records

from test_fixtures import FakeS3Client
from s3_store import S3Store

import hashlib
import tempfile
import os

xml_s3_prefix = "xml"
ftp_s3_prefix = "ftp"


def test_compare_uploads_with_one_file():
    batch_name = "2024-03-22"
    fixture_file_name = "ebz-s7451719-20240328-1.xml"

    current_dir = os.path.dirname(os.path.realpath(__file__))
    fixtures_file = os.path.join(current_dir, "fixtures", fixture_file_name)
    expected_results = {
        "notify_for_batch": batch_name,
        "updated": {
            "ebs9579e": {
                "s3_key": "xml/2024-03-22/ebs9579e.xml",
                "sha256": "4c4a7fb13bf8b7beda96bbe75358eb882a6130592b29f6149a308d82e0356d22",
            },
            "ebs29555e": {
                "s3_key": "xml/2024-03-22/ebs29555e.xml",
                "sha256": "cb719218d3435395a2fa948088bf2c0fe86748dddfb5a2e8e2bbb199f6cf48dd",
            },
        },
        "deleted": None,
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

    with tempfile.TemporaryDirectory() as temp_dir:
        s3_objects = {
            f"{ftp_s3_prefix}/{fixture_file_name}": {
                "Body": file_contents,
            }
        }

        fake_s3_client = FakeS3Client(s3_objects)
        s3_store = S3Store("test_bucket", fake_s3_client)
        results = compare_uploads(
            available_files, extract_marc_records, xml_s3_prefix, temp_dir, s3_store
        )

        assert results == expected_results, "Unexpected results from compare_uploads"

        stored_files = s3_store.list_files("xml/2024-03-22")

        assert len(stored_files) == 3, "Expected 2 files to be uploaded"

        assert "ebs9579e.xml" in stored_files, "Expected ebs9579e.xml to be uploaded"
        assert "ebs29555e.xml" in stored_files, "Expected ebs29555e.xml to be uploaded"
        assert "completed.flag" in stored_files, (
            "Expected completed.flag to be uploaded"
        )

        target_objects = fake_s3_client.list_objects_v2("test_bucket", "xml/2024-03-22")

        for s3_key, value in target_objects.items():
            if s3_key.endswith(".xml"):
                sha256_hash = hashlib.sha256(value["Body"]).hexdigest()
                expected_sha256 = expected_results["updated"][s3_key.split(".")[0]][
                    "sha256"
                ]
                assert sha256_hash == expected_sha256, f"Unexpected sha256 for {s3_key}"


def test_compare_uploads_with_two_files_first_notified():
    batch_name_1 = "2024-03-28"
    batch_name_2 = "2024-04-05"
    fixture_file_name_1 = "ebz-s7451719-20240328-1.xml"
    fixture_file_name_2 = "ebz-s7451719-20240405-1.xml"

    current_dir = os.path.dirname(os.path.realpath(__file__))
    fixtures_file_1 = os.path.join(current_dir, "fixtures", fixture_file_name_1)
    fixtures_file_2 = os.path.join(current_dir, "fixtures", fixture_file_name_2)

    expected_results = {
        "deleted": ["ebs29555e"],
        "notify_for_batch": "2024-04-05",
        "updated": {},
    }

    available_files = {
        batch_name_1: {
            "batch_name": batch_name_1,
            "date": batch_name_1,
            "filename": fixture_file_name_1,
            "upload_location": f"{ftp_s3_prefix}/{fixture_file_name_1}",
            "download_location": fixtures_file_1,
        },
        batch_name_2: {
            "batch_name": batch_name_2,
            "date": batch_name_2,
            "filename": fixture_file_name_2,
            "upload_location": f"{ftp_s3_prefix}/{fixture_file_name_2}",
            "download_location": fixtures_file_2,
        },
    }

    with open(fixtures_file_1, "rb") as f:
        file_contents_1 = f.read()

    with open(fixtures_file_2, "rb") as f:
        file_contents_2 = f.read()

    with tempfile.TemporaryDirectory() as temp_dir:
        s3_objects = {
            f"{ftp_s3_prefix}/{fixture_file_name_1}": {
                "Body": file_contents_1,
            },
            f"{ftp_s3_prefix}/{fixture_file_name_2}": {
                "Body": file_contents_2,
            },
            f"{xml_s3_prefix}/{batch_name_1}/notified.flag": {
                "Body": "",
            },
        }

        fake_s3_client = FakeS3Client(s3_objects)
        s3_store = S3Store("test_bucket", fake_s3_client)
        results = compare_uploads(
            available_files, extract_marc_records, xml_s3_prefix, temp_dir, s3_store
        )

        assert results == expected_results, "Unexpected results from compare_uploads"

        stored_files = s3_store.list_files(f"{xml_s3_prefix}/{batch_name_2}")

        assert len(stored_files) == 2, "Expected 2 files to be uploaded"

        assert "ebs9579e.xml" in stored_files, "Expected ebs9579e.xml to be uploaded"
        assert "completed.flag" in stored_files, (
            "Expected completed.flag to be uploaded"
        )

        target_objects = fake_s3_client.list_objects_v2("test_bucket", "xml/2024-03-22")

        for s3_key, value in target_objects.items():
            if s3_key.endswith(".xml"):
                sha256_hash = hashlib.sha256(value["Body"]).hexdigest()
                expected_sha256 = expected_results["updated"][s3_key.split(".")[0]][
                    "sha256"
                ]
                assert sha256_hash == expected_sha256, f"Unexpected sha256 for {s3_key}"


def test_compare_uploads_with_two_files_first_notified_with_record_modified():
    batch_name_1 = "2024-03-22"
    batch_name_2 = "2024-04-05"
    fixture_file_name_1 = "ebz-s7451719-20240328-1.xml"
    fixture_file_name_2 = "ebz-s7451719-20240405-2.xml"

    current_dir = os.path.dirname(os.path.realpath(__file__))
    fixtures_file_1 = os.path.join(current_dir, "fixtures", fixture_file_name_1)
    fixtures_file_2 = os.path.join(current_dir, "fixtures", fixture_file_name_2)

    expected_results = {
        "deleted": ["ebs29555e"],
        "notify_for_batch": "2024-04-05",
        "updated": {
            "ebs9579e": {
                "s3_key": "xml/2024-04-05/ebs9579e.xml",
                "sha256": "b166aa8eb3c4f85dc95602fbdd920b444dfb6e08f6bc59881ad737878ed04822",
            }
        },
    }

    available_files = {
        batch_name_1: {
            "batch_name": batch_name_1,
            "date": batch_name_1,
            "filename": fixture_file_name_1,
            "upload_location": f"{ftp_s3_prefix}/{fixture_file_name_1}",
            "download_location": fixtures_file_1,
        },
        batch_name_2: {
            "batch_name": batch_name_2,
            "date": batch_name_2,
            "filename": fixture_file_name_2,
            "upload_location": f"{ftp_s3_prefix}/{fixture_file_name_2}",
            "download_location": fixtures_file_2,
        },
    }

    with open(fixtures_file_1, "rb") as f:
        file_contents_1 = f.read()

    with open(fixtures_file_2, "rb") as f:
        file_contents_2 = f.read()

    with tempfile.TemporaryDirectory() as temp_dir:
        s3_objects = {
            f"{ftp_s3_prefix}/{fixture_file_name_1}": {
                "Body": file_contents_1,
            },
            f"{ftp_s3_prefix}/{fixture_file_name_2}": {
                "Body": file_contents_2,
            },
            f"{xml_s3_prefix}/{batch_name_1}/notified.flag": {
                "Body": "",
            },
        }

        fake_s3_client = FakeS3Client(s3_objects)
        s3_store = S3Store("test_bucket", fake_s3_client)
        results = compare_uploads(
            available_files, extract_marc_records, xml_s3_prefix, temp_dir, s3_store
        )

        assert results == expected_results, "Unexpected results from compare_uploads"

        stored_files = s3_store.list_files(f"{xml_s3_prefix}/{batch_name_2}")

        assert len(stored_files) == 2, "Expected 2 files to be uploaded"

        assert "ebs9579e.xml" in stored_files, "Expected ebs9579e.xml to be uploaded"
        assert "completed.flag" in stored_files, (
            "Expected completed.flag to be uploaded"
        )

        target_objects = fake_s3_client.list_objects_v2("test_bucket", "xml/2024-03-22")

        for s3_key, value in target_objects.items():
            if s3_key.endswith(".xml"):
                sha256_hash = hashlib.sha256(value["Body"]).hexdigest()
                expected_sha256 = expected_results["updated"][s3_key.split(".")[0]][
                    "sha256"
                ]
                assert sha256_hash == expected_sha256, f"Unexpected sha256 for {s3_key}"


def test_compare_uploads_with_two_files_no_notified():
    batch_name_1 = "2024-03-22"
    batch_name_2 = "2024-04-05"
    fixture_file_name_1 = "ebz-s7451719-20240328-1.xml"
    fixture_file_name_2 = "ebz-s7451719-20240405-1.xml"

    current_dir = os.path.dirname(os.path.realpath(__file__))
    fixtures_file_1 = os.path.join(current_dir, "fixtures", fixture_file_name_1)
    fixtures_file_2 = os.path.join(current_dir, "fixtures", fixture_file_name_2)

    expected_results = {
        "deleted": None,
        "notify_for_batch": "2024-04-05",
        "updated": {
            "ebs9579e": {
                "s3_key": "xml/2024-04-05/ebs9579e.xml",
                "sha256": "4c4a7fb13bf8b7beda96bbe75358eb882a6130592b29f6149a308d82e0356d22",
            }
        },
    }

    available_files = {
        batch_name_1: {
            "batch_name": batch_name_1,
            "date": batch_name_1,
            "filename": fixture_file_name_1,
            "upload_location": f"{ftp_s3_prefix}/{fixture_file_name_1}",
            "download_location": fixtures_file_1,
        },
        batch_name_2: {
            "batch_name": batch_name_2,
            "date": batch_name_2,
            "filename": fixture_file_name_2,
            "upload_location": f"{ftp_s3_prefix}/{fixture_file_name_2}",
            "download_location": fixtures_file_2,
        },
    }

    with open(fixtures_file_1, "rb") as f:
        file_contents_1 = f.read()

    with open(fixtures_file_2, "rb") as f:
        file_contents_2 = f.read()

    with tempfile.TemporaryDirectory() as temp_dir:
        s3_objects = {
            f"{ftp_s3_prefix}/{fixture_file_name_1}": {
                "Body": file_contents_1,
            },
            f"{ftp_s3_prefix}/{fixture_file_name_2}": {
                "Body": file_contents_2,
            },
        }

        fake_s3_client = FakeS3Client(s3_objects)
        s3_store = S3Store("test_bucket", fake_s3_client)
        results = compare_uploads(
            available_files, extract_marc_records, xml_s3_prefix, temp_dir, s3_store
        )

        assert results == expected_results, "Unexpected results from compare_uploads"

        stored_files = s3_store.list_files(f"xml/{batch_name_2}")

        assert len(stored_files) == 2, "Expected 2 files to be uploaded"
        assert "ebs9579e.xml" in stored_files, "Expected ebs9579e.xml to be uploaded"
        assert "completed.flag" in stored_files, (
            "Expected completed.flag to be uploaded"
        )

        target_objects = fake_s3_client.list_objects_v2("test_bucket", "xml/2024-03-22")

        for s3_key, value in target_objects.items():
            if s3_key.endswith(".xml"):
                sha256_hash = hashlib.sha256(value["Body"]).hexdigest()
                expected_sha256 = expected_results["updated"][s3_key.split(".")[0]][
                    "sha256"
                ]
                assert sha256_hash == expected_sha256, f"Unexpected sha256 for {s3_key}"
