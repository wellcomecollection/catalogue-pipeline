# -*- encoding: utf-8 -*-
"""
Publish a new update window to SNS.
"""

import datetime as dt
import decimal
import json
import os
import logging

import boto3
from build_windows import generate_windows

logger = logging.getLogger(__name__)


def build_window(minutes):
    """Construct the update window."""
    seconds = minutes * 60

    end = dt.datetime.now()
    start = end - dt.timedelta(seconds=seconds)

    return next(generate_windows(start=start, end=end, minutes=minutes))


class EnhancedJSONEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, dt.datetime):
            return obj.isoformat()

        if isinstance(obj, decimal.Decimal):
            if float(obj).is_integer():
                return int(obj)
            else:
                return float(obj)

        return json.JSONEncoder.default(self, obj)


def publish_sns_message(sns_client, topic_arn, message, subject="default-subject"):
    """
    Given a topic ARN and a series of key-value pairs, publish the key-value
    data to the SNS topic.
    """
    response = sns_client.publish(
        TopicArn=topic_arn,
        MessageStructure="json",
        Message=json.dumps({"default": json.dumps(message, cls=EnhancedJSONEncoder)}),
        Subject=subject,
    )

    if response["ResponseMetadata"]["HTTPStatusCode"] == 200:
        logger.debug("SNS: sent notification %s", response["MessageId"])
    else:
        raise RuntimeError(repr(response))

    return response


def main(event=None, _ctxt=None, sns_client=None):
    sns_client = sns_client or boto3.client("sns")

    topic_arn = os.environ["TOPIC_ARN"]
    window_length_minutes = int(os.environ["WINDOW_LENGTH_MINUTES"])
    print(f"topic_arn={topic_arn}, window_length_minutes={window_length_minutes}")

    message = build_window(minutes=window_length_minutes)

    publish_sns_message(
        sns_client=sns_client,
        topic_arn=topic_arn,
        message=message,
        subject="source: sierra_window_generator.main",
    )
