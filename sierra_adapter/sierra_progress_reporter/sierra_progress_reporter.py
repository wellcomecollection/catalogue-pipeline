#!/usr/bin/env python3
"""
Query the S3 bucket containing Sierra progress reports, and log
a report in Slack
"""

import datetime as dt
import itertools
import json
import os

import boto3
import requests

from aws import get_secret_string, list_s3_prefix
from interval_arithmetic import combine_overlapping_intervals, get_intervals


def build_report(sess, bucket, resource_type):
    """
    Generate a complete set of covering windows for a resource type.
    """
    keys = list_s3_prefix(sess, bucket=bucket, prefix=f"completed_{resource_type}/")
    yield from get_intervals(keys=keys)


def chunks(iterable, chunk_size):
    return (iterable[i : i + chunk_size] for i in range(0, len(iterable), chunk_size))


class IncompleteReportError(Exception):
    pass


def process_report(sess, *, bucket, resource_type):
    # Start by consolidating all the windows as best we can.  This is an
    # incremental consolidation: it consolidates the first 1000 keys, then
    # the second 1000 keys, and so on.
    #
    # If there are more keys than it can consolidate in a single invocation,
    # it will at least have made progress and be less likely to time out
    # next time.
    consolidate_windows(sess, bucket=bucket, resource_type=resource_type)

    for iv in build_report(sess, bucket=bucket, resource_type=resource_type):

        # If the first gap is more than 6 hours old, we might have a
        # bug in the Sierra reader.  Raise an exception.
        hours = (dt.datetime.utcnow() - iv.end).total_seconds() / 3600
        if hours > 6:
            raise IncompleteReportError(resource_type)


# https://stackoverflow.com/q/6822725/1558022
def window(seq, n=2):
    """
    Returns a sliding window (of width n) over data from the iterable

        s -> (s0,s1,...s[n-1]), (s1,s2,...,sn),

    """
    it = iter(seq)
    result = tuple(itertools.islice(it, n))
    if len(result) == n:
        yield result
    for elem in it:
        result = result[1:] + (elem,)
        yield result


def prepare_missing_report(sess, *, bucket, resource_type):
    """
    Generate a report for windows that are missing.
    """
    yield ""
    yield f"*missing {resource_type} windows*"

    for iv1, iv2 in window(build_report(sess, bucket=bucket, resource_type=resource_type)):
        missing_start = iv1.end
        missing_end = iv2.start
        if missing_start.date() == missing_end.date():
            yield f"{missing_start.date()}: {missing_start.strftime('%H:%M:%S')} — {missing_end.strftime('%H:%M:%S')}"
        else:
            yield f"{missing_start.strftime('%Y-%m-%d %H:%M:%S')} — {missing_end.strftime('%Y-%m-%d %H:%M:%S')}"

    yield ""


def consolidate_windows(sess, *, bucket, resource_type):
    intervals = get_intervals(keys=list_s3_prefix(sess, bucket=bucket, prefix=f"completed_{resource_type}"))

    s3_client = sess.client("s3")

    for iv, running in combine_overlapping_intervals(intervals):
        if len(running) > 1:
            # Create a consolidated marker that represents the entire
            # interval.  The back-history of Sierra includes >100k windows,
            # so combining them makes reporting faster on subsequent runs.
            window = {"start": iv.start.isoformat(), "end": iv.end.isoformat()}

            consolidated_key = (f"completed_{resource_type}/{json.dumps(window)}")

            s3_client.put_object(Bucket=bucket, Key=consolidated_key, Body=b"")

            # Then clean up the individual intervals that made up the set.
            # We sacrifice granularity for performance.
            for sub_ivs in chunks(running, chunk_size=1000):
                keys = [s.key for s in sub_ivs if s.key != consolidated_key]
                s3_client.delete_objects(
                    Bucket=bucket, Delete={"Objects": [{"Key": k} for k in keys]}
                )


def main(event=None, _ctxt=None):
    sess = boto3.Session()

    s3_client = sess.client("s3")
    bucket = os.environ["BUCKET"]

    slack_webhook = get_secret_string(sess, SecretId="sierra_adapter/critical_slack_webhook")

    errors = []
    error_lines = []

    for resource_type in ("bibs", "items", "holdings", "orders"):
        print(f"Preparing report for {resource_type}…")
        try:
            process_report(sess, bucket=bucket, resource_type=resource_type)
        except IncompleteReportError:
            error_lines.extend(
                prepare_missing_report(
                    sess, bucket=bucket, resource_type=resource_type
                )
            )
            errors.append(resource_type)

    if errors:
        error_lines.insert(0, "There are gaps in the %s data." % "/".join(errors))

        error_lines.append(
            "You can fix this by running `$ python sierra_adapter/build_missing_windows.py` in the root of the catalogue repo."
        )

        slack_data = {
            "username": "sierra-reader",
            "icon_emoji": ":sierra:",
            "attachments": [
                {
                    "color": "#8B4F30",
                    "text": "\n".join(error_lines).strip(),
                    "mrkdwn_in": ["text"],
                }
            ],
        }

        resp = requests.post(
            slack_webhook,
            data=json.dumps(slack_data),
            headers={"Content-Type": "application/json"},
        )
        resp.raise_for_status()


if __name__ == '__main__':
    main()
