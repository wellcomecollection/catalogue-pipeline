# -*- encoding: utf-8 -*-

import boto3
import datetime
import json
from moto import mock_sns, mock_sqs
import os
from unittest.mock import patch

import snapshot_scheduler


@mock_sns
@mock_sqs
def test_writes_message_to_sqs():
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

    public_bucket_name = "public-bukkit"
    public_object_key_v2 = "v2/works.json.gz"

    patched_os_environ = {
        "TOPIC_ARN": topic_arn,
        "PUBLIC_BUCKET_NAME": public_bucket_name,
        "PUBLIC_OBJECT_KEY_V2": public_object_key_v2,
    }

    with patch.dict(os.environ, patched_os_environ, clear=True):
        snapshot_scheduler.main(sns_client=sns_client)

    test_queue_messages = sqs_client.receive_message(
        QueueUrl=test_queue_url,
        MaxNumberOfMessages=10
    )
    messages = [
        json.loads(json.loads(json.loads(m["Body"])["Message"])["default"])
        for m in test_queue_messages["Messages"]
    ]
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
