# -*- encoding: utf-8 -*-

import datetime
import os
from unittest.mock import patch

import snapshot_scheduler

pytest_plugins = "catalogue_aws_fixtures"


def test_writes_message_to_sqs(
    test_topic_arn, mock_sns_client, get_test_topic_messages
):
    public_bucket_name = "public-bukkit"
    public_object_key_v2 = "v2/works.json.gz"

    patched_os_environ = {
        "TOPIC_ARN": test_topic_arn,
        "PUBLIC_BUCKET_NAME": public_bucket_name,
        "PUBLIC_OBJECT_KEY_V2": public_object_key_v2,
    }

    with patch.dict(os.environ, patched_os_environ, clear=True):
        snapshot_scheduler.main(sns_client=mock_sns_client)

    messages = list(get_test_topic_messages())
    assert len(messages) == 1

    snapshot_job = messages[0]
    assert snapshot_job["s3Location"] == {
        "bucket": public_bucket_name,
        "key": public_object_key_v2,
    }
    assert snapshot_job["apiVersion"] == "v2"

    requested_at = datetime.datetime.strptime(
        snapshot_job["requestedAt"], "%Y-%m-%dT%H:%M:%SZ"
    )
    assert (datetime.datetime.now() - requested_at).total_seconds() < 5
