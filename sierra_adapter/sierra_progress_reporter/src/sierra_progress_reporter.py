# -*- encoding: utf-8 -*-
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

from interval_arithmetic import combine_overlapping_intervals, get_intervals


def get_matching_s3_keys(s3_client, bucket, prefix):
    """
    Generate the keys in an S3 bucket that match a given prefix.
    """
    paginator = s3_client.get_paginator("list_objects")
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
        print("Got page of %d objects from S3…" % len(page["Contents"]))
        for s3_object in page["Contents"]:
            yield s3_object["Key"]


def build_report(s3_client, bucket, resource_type):
    """
    Generate a complete set of covering windows for a resource type.
    """
    keys = get_matching_s3_keys(
        s3_client=s3_client, bucket=bucket, prefix=f"windows_{resource_type}_complete"
    )
    yield from get_intervals(keys=keys)


def chunks(iterable, chunk_size):
    return (iterable[i : i + chunk_size] for i in range(0, len(iterable), chunk_size))


class IncompleteReportError(Exception):
    pass


def process_report(s3_client, bucket, resource_type):
    # Start by consolidating all the windows as best we can.  This is an
    # incremental consolidation: it consolidates the first 1000 keys, then
    # the second 1000 keys, and so on.
    #
    # If there are more keys than it can consolidate in a single invocation,
    # it will at least have made progress and be less likely to time out
    # next time.
    consolidate_windows(s3_client=s3_client, bucket=bucket, resource_type=resource_type)

    for iv in build_report(s3_client, bucket, resource_type):

        # If the first gap is more than 6 hours old, we might have a
        # bug in the Sierra reader.  Raise an exception.
        hours = (dt.datetime.now() - iv.end).total_seconds() / 3600
        if hours > 6:
            raise IncompleteReportError(resource_type)


def prepare_present_report(s3_client, bucket, resource_type):
    """
    Generate a report for windows that are present.
    """
    yield ""
    yield f"*{resource_type} windows*"

    for iv in get_consolidated_report(s3_client, bucket, resource_type):
        yield f"{iv.start.isoformat()} – {iv.end.isoformat()}"

    yield ""


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


def print_report(s3_client, bucket, resource_type):
    print("\n".join(prepare_present_report(s3_client, bucket, resource_type)))


def consolidate_windows(s3_client, bucket, resource_type):
    paginator = s3_client.get_paginator("list_objects")
    prefix = f"windows_{resource_type}_complete"

    for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
        keys = [s3_obj["Key"] for s3_obj in page["Contents"]]
        intervals = get_intervals(keys=keys)

        for iv, running in combine_overlapping_intervals(intervals):
            if len(running) > 1:
                # Create a consolidated marker that represents the entire
                # interval.  The back-history of Sierra includes >100k windows,
                # so combining them makes reporting faster on subsequent runs.
                start_str = iv.start.strftime("%Y-%m-%dT%H-%M-%S.%f+00-00")
                end_str = iv.end.strftime("%Y-%m-%dT%H-%M-%S.%f+00-00")

                consolidated_key = (
                    f"windows_{resource_type}_complete/{start_str}__{end_str}"
                )

                s3_client.put_object(Bucket=bucket, Key=consolidated_key, Body=b"")

                # Then clean up the individual intervals that made up the set.
                # We sacrifice granularity for performance.
                for sub_ivs in chunks(running, chunk_size=1000):
                    keys = [s.key for s in sub_ivs if s.key != consolidated_key]
                    s3_client.delete_objects(
                        Bucket=bucket, Delete={"Objects": [{"Key": k} for k in keys]}
                    )


def main(event=None, _ctxt=None):
    s3_client = boto3.client("s3")
    bucket = os.environ["BUCKET"]
    slack_webhook = os.environ["SLACK_WEBHOOK"]

    errors = []
    error_lines = []

    for resource_type in ("bibs", "items"):
        consolidate_windows(
            s3_client=s3_client,
            bucket=bucket,
            resource_type=resource_type
        )

        try:
            process_report(
                s3_client=s3_client, bucket=bucket, resource_type=resource_type
            )
        except IncompleteReportError:
            error_lines.extend(
                prepare_missing_report(
                    s3_client=s3_client, bucket=bucket, resource_type=resource_type
                )
            )
            errors.append(resource_type)

    if errors:
        if errors == ["bibs"]:
            message = "There are gaps in the bib data."
        elif errors == ["items"]:
            message = "There are gaps in the item data."
        else:
            message = "There are gaps in the bib and the item data."

        error_lines.insert(0, message)

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


if __name__ == "__main__":
    s3_client = boto3.client("s3")
    bucket = "wellcomecollection-platform-adapters-sierra"

    for resource_type in ("bibs", "items"):
        consolidate_windows(
            s3_client=s3_client,
            bucket=bucket,
            resource_type=resource_type
        )

        print_report(s3_client=s3_client, bucket=bucket, resource_type=resource_type)
