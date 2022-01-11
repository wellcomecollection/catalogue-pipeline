# -*- encoding: utf-8 -*-
"""
Publish a new Calm window to SNS.
"""

from datetime import date, datetime, timedelta
import os
import boto3

from wellcome_aws_utils.lambda_utils import log_on_error

from window_generator import WindowGenerator, CalmQuery, created_or_modified_date_range


@log_on_error
def main(event=None, _ctxt=None):
    topic_arn = os.environ["TOPIC_ARN"]

    start = (datetime.now() - timedelta(hours=2)).date()
    end = date.today()

    queries = created_or_modified_date_range(start, end)

    # At the beginning of the day lets also check for Calm records without
    # either created or modified dates.
    if start != end:
        queries = [CalmQuery.empty_created_and_modified_date(), *queries]

    sns_client = boto3.client("sns")

    print(f"topic_arn={topic_arn}, start={start}, end={end}")
    WindowGenerator(sns_client, topic_arn, queries).run()
