# -*- encoding: utf-8 -*-

import boto3
import datetime as dt
import json
import os
import mock
from moto import mock_sns, mock_sqs

from sierra_window_generator import build_window, main


class patched_datetime(dt.datetime):
    @classmethod
    def now(cls, tz=None):
        return dt.datetime(2011, 6, 21, 0, 0, 0, 0, tzinfo=tz)


@mock.patch("datetime.datetime", patched_datetime)
def test_build_window():
    assert build_window(minutes=15) == {
        "start": "2011-06-20T23:45:00+00:00",
        "end": "2011-06-21T00:00:00+00:00",
    }


@mock_sns
@mock_sqs
def test_end_to_end():
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

    os.environ["WINDOW_LENGTH_MINUTES"] = "25"

    with mock.patch.dict(os.environ, {"TOPIC_ARN": topic_arn}):
        with mock.patch("datetime.datetime", patched_datetime):
            # This Lambda doesn't read anything from its event or context
            main(sns_client=sns_client)

    test_queue_messages = sqs_client.receive_message(
        QueueUrl=test_queue_url,
        MaxNumberOfMessages=10
    )
    messages = [
        json.loads(json.loads(json.loads(m["Body"])["Message"])["default"])
        for m in test_queue_messages["Messages"]
    ]
    assert len(messages) == 1
    assert messages[0] == {
        "start": "2011-06-20T23:35:00+00:00",
        "end": "2011-06-21T00:00:00+00:00",
    }
