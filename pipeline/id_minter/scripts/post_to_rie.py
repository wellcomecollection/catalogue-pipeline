import sys
import requests
import json
import uuid


def format_message(source_id):
    return {
        "messageId": str(uuid.uuid1()),
        "receiptHandle": "MessageReceiptHandle",
        "body": json.dumps(
            {
                "Type": "Notification",
                "MessageId": str(uuid.uuid1()),
                "TopicArn": "arn:aws:sns:eu-west-1:999999999999:my_upstream_topic",
                "Subject": "Sent from the transformer",
                "Message": source_id,
                "Timestamp": "2024-11-06T10:50:43.532Z",
                "SignatureVersion": "1",
                "Signature": "BigLoadOfBase64==",
                "SigningCertURL": "https://sns.eu-west-1.amazonaws.com/Its_Me-Honest_It_Is.pem",
                "UnsubscribeURL": "https://sns.eu-west-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-1:999999999999:my_upstream_topic",
            }
        ),
        "attributes": {
            "ApproximateReceiveCount": "1",
            "SentTimestamp": "0",
            "SenderId": "123456789012",
            "ApproximateFirstReceiveTimestamp": "1",
        },
        "messageAttributes": {},
        "md5OfBody": "no one cares",
        "eventSource": "aws:sqs",
        "eventSourceARN": "arn:aws:sqs:us-east-1:123456789012:MyQueue",
        "awsRegion": "mars-north-1",
    }


payload = {
    "Records": [format_message(source_id) for source_id in sys.stdin.readlines()]
}
requests.post(
    "http://localhost:9000/2015-03-31/functions/function/invocations",
    data=json.dumps(payload),
)
