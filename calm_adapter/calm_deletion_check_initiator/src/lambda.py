import boto3
import os
from wellcome_aws_utils.lambda_utils import log_on_error

from .deletion_check_initiator import DeletionCheckInitiator

source_table_name = os.environ.get("SOURCE_TABLE_NAME")
reindexer_topic_arn = os.environ.get("REINDEXER_TOPIC_ARN")
deletion_check_initiator = DeletionCheckInitiator(
    session=boto3,
    source_table_name=source_table_name,
    reindexer_topic_arn=reindexer_topic_arn,
)


@log_on_error
def main(event=None, ctx=None):
    print(f"Initiating deletion check for records in {source_table_name}")
    deletion_check_initiator.all_records()
