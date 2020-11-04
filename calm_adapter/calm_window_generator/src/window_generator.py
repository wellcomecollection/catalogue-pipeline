from datetime import timedelta
import json


class WindowGenerator:
    def __init__(self, sns_client, topic_arn, queries):
        self.client = sns_client
        self.topic_arn = topic_arn
        self.queries = queries

    def run(self):
        for query in self.queries:
            self.send_query_to_sns(query)

    def send_query_to_sns(self, query):
        self.client.publish(TopicArn=self.topic_arn, Message=query.sns_message)


class CalmQuery:
    def __init__(self, type, **kwargs):
        self.type = type
        self.kwargs = kwargs

    @property
    def sns_message(self):
        return json.dumps({"type": self.type, **self.kwargs})

    @staticmethod
    def modified_date(date):
        return CalmQuery("ModifiedDate", date=date.isoformat())

    @staticmethod
    def created_or_modified_date(date):
        return CalmQuery("CreatedOrModifiedDate", date=date.isoformat())

    @staticmethod
    def empty_created_and_modified_date():
        return CalmQuery("EmptyCreatedAndModifiedDate")

    @staticmethod
    def ref_no(ref_no):
        return CalmQuery("RefNo", refNo=ref_no)


def created_or_modified_date_range(start, end=None):
    end = end or start
    assert start <= end, "Start window is after end window"
    while start <= end:
        yield CalmQuery.created_or_modified_date(start)
        start += timedelta(days=1)
