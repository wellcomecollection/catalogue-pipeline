import json


def construct_sns_event(ebsco_id: str, s3_bucket: str, s3_key: str, delete: bool):
    """
    Constructs a fake SNS event object mimicking a real event published by the adapter lambda.
    This is only used when running the indexer locally and for unit testing.
    """
    # Construct an SNS message from the passed args
    message = {
        "id": ebsco_id,
        "location": {
            "bucket": s3_bucket,
            "key": s3_key,
        },
        "deleted": delete,
    }

    # In AWS, the SNS object stores the message JSON as a string, so we do the same here
    raw_message = json.dumps(message)
    event = {
        "Records": [{"Sns": {"Message": raw_message}}],
    }

    return event
