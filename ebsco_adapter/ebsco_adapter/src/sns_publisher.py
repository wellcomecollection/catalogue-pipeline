import json
import boto3
import hashlib


class SnsPublisher:
    def __init__(self, sns_topic_arn, sns_client=None):
        self.sns_topic_arn = sns_topic_arn
        if sns_client:
            self.sns_client = sns_client
        else:
            self.sns_client = boto3.client("sns")

    def publish(self, messages, batch_size=10):
        assert batch_size <= 10, "Batch size must be less than or equal to 10."

        batches = [
            messages[i : i + batch_size] for i in range(0, len(messages), batch_size)
        ]
        print(f"Publishing {len(messages)} messages in {len(batches)} batches.")

        for i, batched_messages in enumerate(batches):
            print(f"Publishing batch {i + 1} of {len(batches)} ...")
            batched_requests = []
            for message in batched_messages:
                json_body = json.dumps(message)
                sha256_hash = hashlib.sha256(json_body.encode("utf-8")).hexdigest()
                batched_requests.append(
                    {
                        "Id": sha256_hash,
                        "Message": json.dumps(message),
                    }
                )

                response = self.sns_client.publish_batch(
                    TopicArn=self.sns_topic_arn,
                    PublishBatchRequestEntries=batched_requests,
                )

                if "Failed" in response and len(response["Failed"]) > 0:
                    print(response)
                    raise ValueError(
                        f"Failed to publish messages: {response['Failed']}"
                    )

        print(f"Published {len(messages)} messages in {len(batches)} batches.")
