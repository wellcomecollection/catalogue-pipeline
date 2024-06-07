import json
from datetime import datetime


def construct_sns_event(ebsco_id: str, s3_bucket: str, s3_key: str, delete: bool):
    # Construct an SNS message from the passed args
    message = {
        "id": ebsco_id,
        "location": {
            "bucket": s3_bucket,
            "key": s3_key,
        },
        "deleted": delete,
    }

    # In AWS, the Lambda handler receives the message JSON as a string, so we do the same here
    raw_message = json.dumps(message)
    event = {
        "invoked_at": datetime.utcnow().isoformat(),
        "Records": [{"Sns": {"Message": raw_message}}],
    }

    return event
