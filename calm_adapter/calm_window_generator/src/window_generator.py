from datetime import timedelta
import json

import boto3


class WindowGenerator:

    role_arn = "arn:aws:iam::760097843905:role/platform-developer"

    def __init__(self, topic_arn, start, end=None):
        self.topic_arn = topic_arn
        self.start = start
        self.end = end or start
        assert self.start <= self.end, "Start window is after end window"
        self.client = self.get_sns_client()

    def run(self):
        for window in self.windows:
            self.send_window_to_sns(window)

    @property
    def windows(self):
        while self.start <= self.end:
            yield self.start
            self.start += timedelta(days=1)

    def send_window_to_sns(self, window):
        msg = json.dumps({"date": window.isoformat()})
        self.client.publish(
            TopicArn=self.topic_arn, Message=msg, Subject=f"Window sent by {__file__}"
        )

    def get_sns_client(self):
        sts = boto3.client("sts")
        role = sts.assume_role(
            RoleArn=self.role_arn, RoleSessionName="AssumeRoleSession1"
        )
        credentials = role["Credentials"]
        return boto3.client(
            "sns",
            aws_access_key_id=credentials["AccessKeyId"],
            aws_secret_access_key=credentials["SecretAccessKey"],
            aws_session_token=credentials["SessionToken"],
        )
