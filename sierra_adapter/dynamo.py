import decimal
import json


class DecimalEncoder(json.JSONEncoder):
    def default(self, value):
        if isinstance(value, decimal.Decimal) and int(value) == value:
            return int(value)


def get_table_item_count(session, *, table_name):
    """
    Counts the items in a DynamoDB table.
    """
    dynamo = session.client("dynamodb")
    describe_resp = dynamo.describe_table(TableName=table_name)
    return describe_resp["Table"]["ItemCount"]


def scan_table(session, *, table_name):
    """
    Generates all the items in a DynamoDB table.
    """
    dynamo = session.resource("dynamodb").meta.client
    for page in dynamo.get_paginator("scan").paginate(TableName=table_name):
        yield from page["Items"]
