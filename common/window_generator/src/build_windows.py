#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
Build 'update windows' for the Sierra or TEI adapter pipeline.

Usage:
    build_windows.py --start=<START> --end=<END> [--window_length=<WINDOW_LENGTH>] --resource=<RESOURCE>
    build_windows.py -h | --help

Options:
    --start=<START>         When to start polling
    --end=<END>             The earliest point to stop polling
    --window_length=<WINDOW_LENGTH>
                            The number of minutes per window
    --resource=<RESOURCE>   What sort of resource should we fetch?


This script generates windows to poll for updates, and sends
them to the SNS topic that triggers our pipeline.  You pass it an interval
(start, end), and then it generates windows of length `window_length` that
cover the interval.

For example, calling the script with arguments

    start           = 10:00
    end             = 10:59
    window_length   = 15
    resource        = bibs

which generate four windows:

    ( 10:00 ------------------------------------------------------ 11:00 )
    | 10:00 -- 10:15 |
                     | 10:15 -- 10:30 |
                                      | 10:30 -- 10:45 |
                                                       | 10:45 --- 11:00 |

"""

import datetime as dt
import json
import math

import boto3
import click
import pytz
import tqdm

SOURCES = {
    "sierra-items": "arn:aws:sns:eu-west-1:760097843905:sierra_items_reharvest_windows",
    "sierra-bibs": "arn:aws:sns:eu-west-1:760097843905:sierra_bibs_reharvest_windows",
    "sierra-holdings": "arn:aws:sns:eu-west-1:760097843905:sierra_holdings_reharvest_windows",
    "sierra-orders": "arn:aws:sns:eu-west-1:760097843905:sierra_orders_reharvest_windows",
}


def generate_windows(start, end, minutes):
    current = start.replace(tzinfo=pytz.utc)
    end = end.replace(tzinfo=pytz.utc)
    while current <= end:
        yield {
            "start": current.isoformat(),
            "end": (current + dt.timedelta(minutes=minutes)).isoformat(),
        }
        current += dt.timedelta(minutes=minutes - 1)


@click.command()
@click.option(
    "--start",
    required=True,
    type=click.DateTime(),
    prompt="At what datetime should windows start?",
)
@click.option(
    "--end",
    required=True,
    type=click.DateTime(),
    prompt="At what datetime should windows end?",
)
@click.option(
    "--window-length",
    required=True,
    type=int,
    default=30,
    prompt="How long should the windows be in minutes?",
)
@click.option(
    "--resource",
    required=True,
    type=click.Choice(SOURCES.keys()),
    prompt="Which resource?",
)
def main(start, end, window_length, resource):
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

    topic = SOURCES[resource]
    for window in tqdm.tqdm(
        generate_windows(start, end, window_length),
        total=math.ceil((end - start).total_seconds() / 60 / (window_length - 1)),
    ):
        client.publish(TopicArn=topic, Message=json.dumps(window))


if __name__ == "__main__":
    main()
