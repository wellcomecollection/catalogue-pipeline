import json


def construct_sns_message(s3_bucket: str, s3_key: str, delete: bool):
    """
    Constructs a fake SNS message object mimicking a real message published by the adapter lambda.
    This is only used when running the indexer locally and for unit testing.
    """
    ebsco_id = s3_key.split("/")[-1].split(".")[0]

    # Construct an SNS message from the passed args
    message = {
        "id": ebsco_id,
        "location": {
            "bucket": s3_bucket,
            "key": s3_key,
        },
        "deleted": delete,
    }

    return message


def construct_sqs_event(s3_bucket: str, s3_keys_to_index_or_delete: dict[str, bool]):
    raw_sns_messages = []

    for s3_key, delete in s3_keys_to_index_or_delete.items():
        sns_message = construct_sns_message(s3_bucket, s3_key, delete)
        raw_sns_messages.append({"body": json.dumps(sns_message)})

    event = {"Records": raw_sns_messages}

    return event
