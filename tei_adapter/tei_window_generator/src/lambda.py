# -*- encoding: utf-8 -*-
"""
Publish a new Calm window to SNS.
"""

from datetime import datetime, timedelta
import os
import boto3
import pytz

from wellcome_aws_utils.lambda_utils import log_on_error
from wellcome_aws_utils.sns_utils import publish_sns_message

@log_on_error
def main(event=None, _ctxt=None):
    window_minutes = os.environ.get("WINDOW_LENGTH_MINUTES", 45)
    topic_arn = os.environ["TOPIC_ARN"]

    start = (datetime.now() - timedelta(minutes=window_minutes))
    end = datetime.now()

    start = start.replace(tzinfo=pytz.utc)
    end = end.replace(tzinfo=pytz.utc)
    message =  {
        "start": start.isoformat(),
        "end": end.isoformat()
    }
    sns_client = boto3.client("sns")
    publish_sns_message(
        sns_client=sns_client,
        topic_arn=topic_arn,
        message=message,
        subject="source: tei_window_generator.main",
    )
