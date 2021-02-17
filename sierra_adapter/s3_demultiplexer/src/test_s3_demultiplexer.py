# -*- encoding: utf-8 -*-

import boto3
import json
from moto import mock_s3, mock_sns, mock_sqs
import os
from unittest import mock

from s3_demultiplexer import main


@mock_s3
@mock_sns
@mock_sqs
def test_end_to_end_demultiplexer():
    s3_client = boto3.client("s3", region_name="eu-west-1")
    sns_client = boto3.client("sns", region_name="eu-west-1")
    sqs_client = boto3.client("sqs", region_name="eu-west-1")

    test_topic = sns_client.create_topic(Name="test-topic")
    topic_arn = test_topic["TopicArn"]

    test_queue = sqs_client.create_queue(QueueName="test-queue")
    test_queue_url = test_queue["QueueUrl"]
    test_queue_attributes = sqs_client.get_queue_attributes(
        QueueUrl=test_queue_url,
        AttributeNames=["QueueArn"]
    )
    test_queue_arn = test_queue_attributes["Attributes"]["QueueArn"]
    sns_client.subscribe(
        TopicArn=topic_arn,
        Protocol="sqs",
        Endpoint=test_queue_arn
    )

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

    with mock.patch.dict(os.environ, {"TOPIC_ARN": topic_arn}):
        main(event=event, s3_client=s3_client, sns_client=sns_client)

    test_queue_messages = sqs_client.receive_message(
        QueueUrl=test_queue_url,
        MaxNumberOfMessages=10
    )
    actual_messages = [
        json.loads(json.loads(json.loads(m["Body"])["Message"])["default"])
        for m in test_queue_messages["Messages"]
    ]
    assert actual_messages == records
