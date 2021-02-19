import json
import math
from tqdm import tqdm


class DeletionCheckInitiator:
    # These should match the source/destination in the reindexer_jobs config
    reindex_src = "calm"
    reindex_dest = "calm_deletion_checker"
    reindex_job_config_id = f"{reindex_src}--{reindex_dest}"

    def __init__(
        self, dynamo_client, sns_client, reindexer_topic_arn, source_table_name
    ):
        self.dynamo_client = dynamo_client
        self.sns_client = sns_client
        self.reindexer_topic_arn = reindexer_topic_arn
        self.source_table_name = source_table_name

    def __get_n_reindexer_segments(self):
        """
        We need to tell the reindexer how many segments to use.
        Each segment should contain ~1000 records, so we don't
        exhaust the memory in the reindexer.
        """
        try:
            table_description = self.dynamo_client.describe_table(
                TableName=self.source_table_name
            )
            item_count = table_description["Table"]["ItemCount"]
        except (KeyError, self.dynamo_client.exceptions.ResourceNotFoundException):
            raise Exception(f"No such table: {self.source_table_name}") from None

        return int(math.ceil(item_count / 900))

    def __publish_messages(self, messages, n_messages=None):
        for message in tqdm(messages, total=n_messages):
            resp = self.sns_client.publish(
                TopicArn=self.reindexer_topic_arn,
                MessageStructure="json",
                Message=json.dumps(
                    {
                        "default": json.dumps(
                            {
                                "jobConfigId": self.reindex_job_config_id,
                                "parameters": message,
                            }
                        )
                    }
                ),
            )
            assert resp["ResponseMetadata"]["HTTPStatusCode"] == 200, resp

    def all_records(self):
        n_reindexer_segments = self.__get_n_reindexer_segments()
        reindexer_segments = (
            {
                "segment": segment_idx,
                "totalSegments": n_reindexer_segments,
                "type": "CompleteReindexParameters",
            }
            for segment_idx in range(n_reindexer_segments)
        )

        self.__publish_messages(reindexer_segments, n_messages=n_reindexer_segments)

    def specific_records(self, ids):
        for identifier in ids:
            # While this requires an extra DynamoDB query per ID, it saves
            # the time/confusion of having to wait for the reindexer to do nothing
            try:
                resp = self.dynamo_client.get_item(
                    TableName=self.source_table_name, Key={"id": {"S": identifier}}
                )
            except self.dynamo_client.exceptions.ResourceNotFoundException:
                raise Exception(f"No such table: {self.source_table_name}") from None
            if not resp or "Item" not in resp:
                raise Exception(
                    f"Specified ID {identifier} does not exist in source table {self.source_table_name}"
                )

        self.__publish_messages(
            messages=[{"ids": ids, "type": "SpecificReindexParameters"}]
        )
