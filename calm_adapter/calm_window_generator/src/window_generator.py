from datetime import timedelta
import json


class WindowGenerator:
    def __init__(self, sns_client, topic_arn, start, end=None):
        self.client = sns_client
        self.topic_arn = topic_arn
        self.start = start
        self.end = end or start
        assert self.start <= self.end, "Start window is after end window"

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
        self.client.publish(TopicArn=self.topic_arn, Message=msg)
