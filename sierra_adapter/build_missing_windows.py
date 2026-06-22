#!/usr/bin/env python
# -*- encoding: utf-8 -*-

import collections
import datetime as dt
import json
import os
import sys
import time

import boto3
import click

sys.path.append(os.path.join(os.path.dirname(__file__), "sierra_progress_reporter"))
sys.path.append(
    os.path.join(os.path.dirname(__file__), "..", "common", "window_generator", "src")
)

from build_windows import generate_windows  # noqa
from interval_arithmetic import combine_overlapping_intervals  # noqa
from sierra_progress_reporter import build_report  # noqa

BUCKET = "wellcomecollection-platform-sierra-adapter-20200604"

RESOURCE_TYPES = ("bibs", "items", "holdings", "orders")


def sliding_window(iterable):
    """Returns a sliding window (of width 2) over data from the iterable."""
    result = collections.deque([], maxlen=2)

    for elem in iterable:
        result.append(elem)
        if len(result) == 2:
            yield tuple(list(result))


def get_missing_windows(report, window_length_minutes=2):
    """Given a report of saved Sierra windows, emit the gaps.

    The completed windows we read from S3 frequently overlap (windows are
    generated with a deliberate overlap, and consolidated markers can span the
    individual windows that re-covered a gap).  We therefore first merge the
    intervals into a disjoint covering set, and only emit windows for a genuine,
    positive-duration gap between two covering intervals.

    Walking the *raw* intervals instead would treat a sub-second overlap (or two
    intervals that merely touch) as a gap and inflate it into a full window —
    re-harvesting data that is already present.
    """
    # combine_overlapping_intervals expects intervals sorted by start; don't
    # rely on the order S3 happens to return keys in.
    intervals = sorted(report, key=lambda iv: iv.start)
    covering = [lower for lower, _ in combine_overlapping_intervals(iter(intervals))]

    for interval_1, interval_2 in sliding_window(covering):
        # Only a real gap if the next interval starts *after* the previous one
        # ends.  Overlapping or touching intervals leave no uncovered time.
        if interval_2.start <= interval_1.end:
            continue

        # Suppose we have two disjoint covering intervals:
        #
        #   (-- interval_1 --)
        #                          (-- interval_2 --)
        #
        # We're missing any records created between the *end* of interval_1
        # and the *start* of interval_2, so we use these (padded by a second on
        # either side) as the basis for our new windows.
        missing_start = interval_1.end - dt.timedelta(seconds=1)
        missing_end = interval_2.start + dt.timedelta(seconds=1)

        yield from generate_windows(
            start=missing_start, end=missing_end, minutes=window_length_minutes
        )


def _assume_platform_session():
    sts = boto3.client("sts")
    response = sts.assume_role(
        RoleArn="arn:aws:iam::760097843905:role/platform-developer",
        RoleSessionName="platform",
    )
    return boto3.Session(
        aws_access_key_id=response["Credentials"]["AccessKeyId"],
        aws_secret_access_key=response["Credentials"]["SecretAccessKey"],
        aws_session_token=response["Credentials"]["SessionToken"],
        region_name="eu-west-1",
    )


@click.command()
@click.option(
    "--dry-run",
    is_flag=True,
    help="Compute and print the missing windows but publish nothing to SNS.",
)
@click.option(
    "--throttle",
    type=float,
    default=1.0,
    show_default=True,
    help="Seconds to sleep between SNS publishes (paces load on the Sierra API). "
    "Pass 0 to publish as fast as possible.",
)
@click.option(
    "--window-length",
    type=int,
    default=2,
    show_default=True,
    help="Length of each generated window, in minutes.",
)
@click.option(
    "--yes",
    is_flag=True,
    help="Skip the confirmation prompt before publishing.",
)
def main(dry_run, throttle, window_length, yes):
    """Detect gaps in the Sierra adapter's completed windows and re-publish them."""
    session = _assume_platform_session()

    # Materialise the windows per resource type so we can summarise before
    # publishing anything.
    windows_by_type = {}
    for resource_type in RESOURCE_TYPES:
        report = build_report(session, bucket=BUCKET, resource_type=resource_type)
        windows_by_type[resource_type] = list(
            get_missing_windows(report, window_length_minutes=window_length)
        )

    total = sum(len(w) for w in windows_by_type.values())
    click.echo("Missing windows:")
    for resource_type in RESOURCE_TYPES:
        click.echo(f"  {resource_type}: {len(windows_by_type[resource_type])}")
    click.echo(f"  total: {total}")

    if total == 0:
        click.echo("No missing windows — nothing to do.")
        return

    if dry_run:
        click.echo("\n--dry-run: the following windows would be published:")
        for resource_type in RESOURCE_TYPES:
            for window in windows_by_type[resource_type]:
                click.echo(f"  {resource_type}: {json.dumps(window)}")
        return

    if not yes:
        click.confirm(
            f"\nPublish {total} window(s) to SNS "
            f"(throttled at {throttle}s between publishes)?",
            abort=True,
        )

    client = session.client("sns")
    for resource_type in RESOURCE_TYPES:
        topic_arn = f"arn:aws:sns:eu-west-1:760097843905:sierra_{resource_type}_windows"
        for window in windows_by_type[resource_type]:
            click.echo(f"{resource_type}: {json.dumps(window)}")
            client.publish(
                TopicArn=topic_arn,
                Message=json.dumps(window),
                Subject="Window sender: missing windows script",
            )
            if throttle:
                time.sleep(throttle)


if __name__ == "__main__":
    main()
