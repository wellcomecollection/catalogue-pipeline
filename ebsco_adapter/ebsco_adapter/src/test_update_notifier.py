from update_notifier import update_notifier
from test_fixtures import FakeS3Client, FakeSnsClient
from s3_store import S3Store
from sns_publisher import SnsPublisher


def test_update_notifier():
    s3_bucket = "test_bucket"
    xml_s3_prefix = "test_prefix"
    topic_arn = "test_topic_arn"
    notify_for_batch = "test_batch"

    fake_s3_client = FakeS3Client()
    fake_sns_client = FakeSnsClient()

    sns_publisher = SnsPublisher(topic_arn, fake_sns_client)
    s3_store = S3Store(s3_bucket, fake_s3_client)

    number_of_updates = 100
    number_of_deleted = 50

    updates = {
        "updated": {
            f"update-{i}": {
                "s3_key": f"test_prefix/update-{i}.xml",
                "sha256": "test_sha256",
            } for i in range(number_of_updates)
        },
        "deleted": [f"delete-{i}" for i in range(number_of_deleted)]
    }

    expected_update_messages = [
        {
            "id": f"update-{i}",
            "location": {
                "bucket": s3_bucket,
                "key": f"test_prefix/update-{i}.xml",
            },
            "version": 1,
            "deleted": False,
            "sha256": "test_sha256",
        } for i in range(number_of_updates)
    ]

    expected_deleted_messages = [
        {
            "id": f"delete-{i}",
            "location": None,
            "version": 1,
            "deleted": True,
            "sha256": None,
        } for i in range(number_of_deleted)
    ]

    expected_messages = expected_update_messages + expected_deleted_messages

    update_notifier(updates, notify_for_batch, s3_store, s3_bucket, xml_s3_prefix, sns_publisher)
    published_messages = fake_sns_client.test_get_published_messages()

    assert len(published_messages) == number_of_updates + number_of_deleted, \
        f"Expected {number_of_updates} update messages to be published, but got {len(published_messages)}"

    assert published_messages == expected_messages
