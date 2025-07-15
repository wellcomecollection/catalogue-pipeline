#!/usr/bin/env python
# -*- encoding: utf-8 -*-

import collections
import datetime as dt
import json
import os
import sys

import boto3

sys.path.append(os.path.join(os.path.dirname(__file__), "sierra_progress_reporter"))
sys.path.append(
    os.path.join(os.path.dirname(__file__), "sierra_window_generator", "src")
)
sys.path.append(
    os.path.join(os.path.dirname(__file__), "..", "common", "window_generator", "src")
)

from build_windows import generate_windows  # noqa
from sierra_progress_reporter import build_report  # noqa

BUCKET = "wellcomecollection-platform-sierra-adapter-20200604"


def sliding_window(iterable):
    """Returns a sliding window (of width 2) over data from the iterable."""
    result = collections.deque([], maxlen=2)

    for elem in iterable:
        result.append(elem)
        if len(result) == 2:
            yield tuple(list(result))


def get_missing_windows(report):
    """Given a report of saved Sierra windows, emit the gaps."""
    # Suppose we get two windows:
    #
    #   (-- window_1 --)
    #                       (-- window_2 --)
    #
    # We're missing any records created between the *end* of window_1
    # and the *start* of window_2, so we use these as the basis for
    # our new window.
    for interval_1, interval_2 in sliding_window(report):
        missing_start = interval_1.end - dt.timedelta(seconds=1)
        missing_end = interval_2.start + dt.timedelta(seconds=1)

        yield from generate_windows(start=missing_start, end=missing_end, minutes=2)


if __name__ == "__main__":
    sts = boto3.client("sts")
    response = sts.assume_role(
        RoleArn="arn:aws:iam::760097843905:role/platform-developer",
        RoleSessionName="platform",
    )
    session = boto3.Session(
        aws_access_key_id=response["Credentials"]["AccessKeyId"],
        aws_secret_access_key=response["Credentials"]["SecretAccessKey"],
        aws_session_token=response["Credentials"]["SessionToken"],
        region_name="eu-west-1",
    )

    client = session.client("sns")

    for resource_type in ("bibs", "items", "holdings", "orders"):
        report = build_report(session, bucket=BUCKET, resource_type=resource_type)
        for missing_window in get_missing_windows(report):
            print(missing_window)
            client.publish(
                TopicArn=f"arn:aws:sns:eu-west-1:760097843905:sierra_{resource_type}_windows",
                Message=json.dumps(missing_window),
                Subject=f"Window sender {__file__[100 - 15:]}",
            )
