import boto3
import os

from deletion_check_initiator import DeletionCheckInitiator

source_table_name = os.environ.get("SOURCE_TABLE_NAME")
reindexer_topic_arn = os.environ.get("REINDEXER_TOPIC_ARN")
deletion_check_initiator = DeletionCheckInitiator(
    dynamo_client=boto3.client("dynamodb"),
    sns_client=boto3.client("sns"),
    source_table_name=source_table_name,
    reindexer_topic_arn=reindexer_topic_arn,
)


def main(event=None, ctx=None):
    print(f"Initiating deletion check for records in {source_table_name}")
    deletion_check_initiator.all_records()
