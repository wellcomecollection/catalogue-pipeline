# -*- encoding: utf-8 -*-

import boto3
import json
import os
from unittest import mock

from s3_demultiplexer import main


@mock_s3
def test_end_to_end_demultiplexer(
    mock_sns_client, test_topic_arn, get_test_topic_messages
):
    s3_client = boto3.client("s3", region_name="eu-west-1")

    records = [
        {"colour": "red", "letter": "R"},
        {"colour": "green", "letter": "G"},
        {"colour": "blue", "letter": "B"},
    ]

    s3_client.create_bucket(Bucket="bukkit")
    s3_client.put_object(Bucket="bukkit", Key="test0001.json", Body=json.dumps(records))

    event = {
        "Records": [
            {
                "eventTime": "1970-01-01T00:00:00.000Z",
                "eventName": "event-type",
                "s3": {
                    "bucket": {"name": "bukkit"},
                    "object": {
                        "key": "test0001.json",
                        "size": len(json.dumps(records)),
                        "versionId": "v2",
                    },
                },
            }
        ]
    }

    with mock.patch.dict(os.environ, {"TOPIC_ARN": test_topic_arn}):
        main(event=event, s3_client=s3_client, sns_client=mock_sns_client)

    messages = get_test_topic_messages()
    assert list(messages) == records
