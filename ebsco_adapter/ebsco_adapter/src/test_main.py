import json
import os
import tempfile

from test_fixtures import FakeS3Client, FakeSnsClient, FakeEbscoFtp
from s3_store import S3Store
from sns_publisher import SnsPublisher

from main import run_reindex, run_process

xml_s3_prefix = "xml"
ftp_s3_prefix = "ftp"
topic_arn = "test_topic_arn"
test_bucket = "test_bucket"
invoked_at = "2023-01-01T00:00:00Z"

ebs9579e = {
    "id": "ebs9579e",
    "location": {
        "bucket": "wellcomecollection-platform-ebsco-adapter",
        "key": "dev/xml/2024-03-22/ebs9579e.xml",
    },
    "version": 20240322,
    "deleted": False,
    "sha256": "4c4a7fb13bf8b7beda96bbe75358eb882a6130592b29f6149a308d82e0356d22",
    "time": invoked_at,
}

ebs29555e = {
    "id": "ebs29555e",
    "location": {
        "bucket": "wellcomecollection-platform-ebsco-adapter",
        "key": "dev/xml/2024-03-22/ebs29555e.xml",
    },
    "version": 20240322,
    "deleted": False,
    "sha256": "cb719218d3435395a2fa948088bf2c0fe86748dddfb5a2e8e2bbb199f6cf48dd",
    "time": invoked_at,
}

ebs9579e_20240405 = {
    "id": "ebs9579e",
    "location": {
        "bucket": "wellcomecollection-platform-ebsco-adapter",
        "key": "dev/xml/2024-04-05/ebs9579e.xml",
    },
    "version": 20240405,
    "deleted": False,
    "sha256": "b166aa8eb3c4f85dc95602fbdd920b444dfb6e08f6bc59881ad737878ed04822",
    "time": invoked_at,
}

ebs29555e_20240405_deleted = {
    "id": "ebs29555e",
    "location": None,
    "version": 20240405,
    "deleted": True,
    "sha256": None,
    "time": invoked_at,
}


def test_run_process():
    batch_name_1 = "2024-03-28"
    batch_name_2 = "2024-04-05"

    fixture_file_name_1 = "ebz-s7451719-20240328-1.xml"
    fixture_file_name_2 = "ebz-s7451719-20240405-2.xml"

    current_dir = os.path.dirname(os.path.realpath(__file__))
    fixtures_file_1 = os.path.join(current_dir, "fixtures", fixture_file_name_1)
    fixtures_file_2 = os.path.join(current_dir, "fixtures", fixture_file_name_2)

    with open(fixtures_file_1, "rb") as f:
        file_contents_1 = f.read()

    with open(fixtures_file_2, "rb") as f:
        file_contents_2 = f.read()

    with tempfile.TemporaryDirectory() as temp_dir:
        s3_objects = {
            f"dev/{ftp_s3_prefix}/{fixture_file_name_1}": {
                "Body": file_contents_1,
            },
            f"dev/{xml_s3_prefix}/{batch_name_1}/completed.flag": {
                "Body": json.dumps(
                    {
                        "ebs9579e": {
                            "s3_key": f"xml/{batch_name_1}/ebs9579e.xml",
                            "sha256": "4c4a7fb13bf8b7beda96bbe75358eb882a6130592b29f6149a308d82e0356d22",
                        },
                        "ebs29555e": {
                            "s3_key": f"xml/{batch_name_1}/ebs29555e.xml",
                            "sha256": "cb719218d3435395a2fa948088bf2c0fe86748dddfb5a2e8e2bbb199f6cf48dd",
                        },
                    }
                ).encode()
            },
            f"dev/{xml_s3_prefix}/{batch_name_1}/notified.flag": {
                "Body": "",
            },
        }

        fake_s3_client = FakeS3Client(s3_objects)
        s3_store = S3Store(test_bucket, fake_s3_client)

        fake_sns_client = FakeSnsClient()
        sns_publisher = SnsPublisher(topic_arn, fake_sns_client)

        fake_ebsco_ftp = FakeEbscoFtp(
            {
                "ebz-s7451719-20240328-1.xml": file_contents_1,
                "ebz-s7451719-20240405-1.xml": file_contents_2,
            }
        )

        print("\n--- Running test ingest process ---")
        run_process(temp_dir, fake_ebsco_ftp, s3_store, sns_publisher, invoked_at)

        files_ftp = s3_store.list_files(f"dev/{ftp_s3_prefix}")
        assert files_ftp == [
            "ebz-s7451719-20240328-1.xml",
            "ebz-s7451719-20240405-1.xml",
        ]

        # get the number of files in the s3 bucket under the xml prefix
        files_xml_batch_2 = s3_store.list_files(f"dev/{xml_s3_prefix}/{batch_name_2}")
        assert files_xml_batch_2 == [
            "ebs9579e.xml",
            "completed.flag",
            "notified.flag",
        ]

        published_messages = fake_sns_client.test_get_published_messages()

        for msg in published_messages:
            print(msg)

        expected_messages = [ebs9579e_20240405, ebs29555e_20240405_deleted]

        for msg in expected_messages:
            print(msg)

        assert published_messages == expected_messages


def test_run_reindex():
    fixture_file_name = "ebz-s7451719-20240328-1.xml"
    current_dir = os.path.dirname(os.path.realpath(__file__))
    fixtures_file = os.path.join(current_dir, "fixtures", fixture_file_name)

    with open(fixtures_file, "rb") as f:
        file_contents = f.read()

    with tempfile.TemporaryDirectory() as temp_dir:
        s3_objects = {
            f"{ftp_s3_prefix}/{fixture_file_name}": {
                "Body": file_contents,
            }
        }

        fake_s3_client = FakeS3Client(s3_objects)
        s3_store = S3Store(test_bucket, fake_s3_client)

        fake_sns_client = FakeSnsClient()
        sns_publisher = SnsPublisher(topic_arn, fake_sns_client)

        fake_ebsco_ftp = FakeEbscoFtp(
            {
                "ebz-s7451719-20240322-1.xml": file_contents,
            }
        )
        print("\n--- Running test ingest process ---")
        run_process(temp_dir, fake_ebsco_ftp, s3_store, sns_publisher, invoked_at)

        # get the number of files in the s3 bucket under the xml prefix
        files = s3_store.list_files(f"dev/{xml_s3_prefix}")

        assert files == [
            "ebs9579e.xml",
            "ebs29555e.xml",
            "completed.flag",
            "notified.flag",
        ]

        published_messages = fake_sns_client.test_get_published_messages()
        assert published_messages == [ebs9579e, ebs29555e]

        fake_sns_client.test_reset()
        assert fake_sns_client.test_get_published_messages() == []

        print("\n--- Running test reindex with type full ---")
        run_reindex(s3_store, sns_publisher, invoked_at, "full")

        reindex_published_messages = fake_sns_client.test_get_published_messages()
        assert reindex_published_messages == [ebs9579e, ebs29555e]

        fake_sns_client.test_reset()
        assert fake_sns_client.test_get_published_messages() == []

        print("\n--- Running test reindex with type partial ---")
        run_reindex(s3_store, sns_publisher, invoked_at, "partial", ["ebs9579e"])

        reindex_published_messages = fake_sns_client.test_get_published_messages()
        assert reindex_published_messages == [ebs9579e]
