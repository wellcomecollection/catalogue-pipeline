"""
When some documents fail at the transformer stage, their messages are sent to the corresponding DLQ.

This script looks at messages on that DLQ, and downloads the corresponding source files that are
failing in the transformer, so that you can examine them and work out why.
"""

import click
import boto3
import json
from pprint import pprint



@click.command()
@click.argument("pipeline_date")
@click.argument("transformer_name")
@click.argument("max_messages", type=click.INT)
def main(pipeline_date, transformer_name, max_messages):
    queue_name = f"catalogue-{pipeline_date}_transformer_{transformer_name}_input_dlq"
    session = get_session_with_role("arn:aws:iam::760097843905:role/platform-read_only")
    sqs_client = session.client("sqs")
    s3_client = session.client("s3")
    messages_pulled = 0
    while messages_pulled < max_messages:
        messages = sqs_client.receive_message(
            QueueUrl=f"https://sqs.eu-west-1.amazonaws.com/760097843905/{queue_name}",
            MaxNumberOfMessages=10,
            WaitTimeSeconds=20,
        )["Messages"]
        if not messages:
            break
        for message in messages:
            messages_pulled += 1
            actual_message = unwrap_message(message)
            pprint(actual_message)
            s3_client.download_file(
                actual_message["location"]["bucket"],
                actual_message["location"]["key"],
                f"{actual_message['id']}.json",
            )


def get_session_with_role(role_arn):
    """
    Returns a boto3.Session that uses the given role ARN.
    """
    sts_client = boto3.client("sts")

    assumed_role_object = sts_client.assume_role(
        RoleArn=role_arn, RoleSessionName="AssumeRoleSession1"
    )
    credentials = assumed_role_object["Credentials"]
    return boto3.Session(
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
    )


def unwrap_message(msg):
    """

    :param msg: The whole message representing an SQS Message
    :return: The actual messagey bit that we are interested in.
    """
    return json.loads(json.loads(msg["Body"])["Message"])


if __name__ == "__main__":
    main()
