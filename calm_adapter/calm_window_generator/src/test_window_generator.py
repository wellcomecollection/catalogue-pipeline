from window_generator import WindowGenerator, CalmQuery, created_or_modified_date_range

from datetime import datetime


class FakeSnsClient:
    publish_calls = None

    def publish(self, TopicArn, Message):
        # create or append to list of publish calls
        if self.publish_calls is None:
            self.publish_calls = []

        self.publish_calls.append((TopicArn, Message))

        pass


def test_window_generator():
    sns_client = FakeSnsClient()

    start_date = datetime(2021, 1, 1)

    queries = created_or_modified_date_range(start_date, start_date)

    window_generator = WindowGenerator(sns_client, "topic_arn", queries)
    window_generator.run()

    assert sns_client.publish_calls == [
        (
            "topic_arn",
            CalmQuery("CreatedOrModifiedDate", date="2021-01-01T00:00:00").sns_message,
        )
    ]
