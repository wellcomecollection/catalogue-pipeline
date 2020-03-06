# -*- encoding: utf-8 -*-
"""
Publish a new Calm window to SNS.
"""

from datetime import date, datetime, timedelta
import os
import boto3

from wellcome_aws_utils.lambda_utils import log_on_error

from window_generator import WindowGenerator


@log_on_error
def main(event=None, _ctxt=None):
    crossover_hours = os.environ.get("CROSSOVER_HOURS", 2)
    topic_arn = os.environ["TOPIC_ARN"]

    start = (datetime.now() - timedelta(hours=crossover_hours)).date()
    end = date.today()

    sns_client = boto3.client("sns")

    print(f"topic_arn={topic_arn}, start={start}, end={end}")
    WindowGenerator(sns_client, topic_arn, start, end).run()
