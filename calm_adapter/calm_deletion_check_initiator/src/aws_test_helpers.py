import boto3
import json
from moto import mock_aws
import pytest
from random import randint


@pytest.fixture(scope="function")
def mock_sns_client():
    with mock_aws():
        yield boto3.client("sns", region_name="eu-west-1")


@pytest.fixture(scope="function")
def mock_sqs_client():
    with mock_aws():
        yield boto3.client("sqs", region_name="eu-west-1")


@pytest.fixture(scope="function")
def test_topic_arn(mock_sns_client):
    test_topic = mock_sns_client.create_topic(Name=f"test-topic-{randint(0, 999):03d}")
    yield test_topic["TopicArn"]


@pytest.fixture(scope="function")
def get_test_topic_messages(test_topic_arn, mock_sqs_client, mock_sns_client):
    """
    Because sns topics don't know what messages they're broadcasting, we need
    to subscribe something (an SQS queue in this case) to our test topic in order
    to see the messages. This is the method used in the moto tests:
    https://github.com/spulec/moto/blob/8befcb6a48e5e8040058391190c3d6a50ea40c69/tests/test_sns/test_publishing_boto3.py#L35-L49

    This fixture provides a function that, once called, yields until there
    are no more messages on the internal queue.
    """
    test_queue = mock_sqs_client.create_queue(
        QueueName=f"test-queue-{randint(0, 999):03d}"
    )
    test_queue_url = test_queue["QueueUrl"]
    test_queue_attributes = mock_sqs_client.get_queue_attributes(
        QueueUrl=test_queue_url, AttributeNames=["QueueArn"]
    )
    test_queue_arn = test_queue_attributes["Attributes"]["QueueArn"]

    mock_sns_client.subscribe(
        TopicArn=test_topic_arn, Protocol="sqs", Endpoint=test_queue_arn
    )

    def get_messages():
        yield from (
            json.loads(json.loads(json.loads(m["Body"])["Message"])["default"])
            for m in _get_queue_messages(mock_sqs_client, test_queue_url)
        )

    return get_messages


def _get_queue_messages(sqs_client, url):
    while True:
        resp = sqs_client.receive_message(QueueUrl=url, MaxNumberOfMessages=10)

        try:
            yield from resp["Messages"]
        except KeyError:
            return
