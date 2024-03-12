import boto3
import pytest
from moto import mock_dynamodb2
from deletion_check_initiator import DeletionCheckInitiator

from test_aws import *


@pytest.fixture(scope="function")
def mock_dynamo_client():
    with mock_dynamodb2():
        yield boto3.client("dynamodb", region_name="eu-west-1")


@pytest.fixture(scope="function")
def test_dynamo_table(mock_dynamo_client):
    table = mock_dynamo_client.create_table(
        TableName="test-table",
        AttributeDefinitions=[{"AttributeName": "id", "AttributeType": "S"}],
        KeySchema=[{"AttributeName": "id", "KeyType": "HASH"}],
    )
    table_name = table["TableDescription"]["TableName"]

    yield table_name

    mock_dynamo_client.delete_table(TableName=table_name)


@pytest.fixture(scope="function")
def put_records(mock_dynamo_client, test_dynamo_table):
    def _put_records(records):
        for record in records:
            mock_dynamo_client.put_item(
                TableName=test_dynamo_table, Item={"id": {"S": record["id"]}}
            )

    yield _put_records


@pytest.fixture(scope="function")
def deletion_check_initiator(
    mock_dynamo_client, mock_sns_client, test_topic_arn, test_dynamo_table
):
    yield DeletionCheckInitiator(
        dynamo_client=mock_dynamo_client,
        sns_client=mock_sns_client,
        reindexer_topic_arn=test_topic_arn,
        source_table_name=test_dynamo_table,
    )


def test_all_records(deletion_check_initiator, put_records, get_test_topic_messages):
    n_records = 2000
    lots_of_records = ({"id": f"{i:05d}"} for i in range(n_records))
    put_records(lots_of_records)

    deletion_check_initiator.all_records()

    messages = list(get_test_topic_messages())
    assert len(messages) >= (n_records / 1000)

    first_message = messages[0]
    assert first_message["jobConfigId"] == "calm--calm_deletion_checker"
    assert first_message["parameters"]["type"] == "CompleteReindexParameters"

    total_segments = first_message["parameters"]["totalSegments"]
    assert max([m["parameters"]["segment"] for m in messages]) == (total_segments - 1)


def test_specific_records(
    deletion_check_initiator, put_records, get_test_topic_messages
):
    records = [{"id": f"{i:05d}"} for i in range(10)]
    put_records(records)

    chosen_record_ids = [r["id"] for r in records[:3]]
    deletion_check_initiator.specific_records(chosen_record_ids)

    messages = list(get_test_topic_messages())
    assert len(messages) == 1
    assert messages[0]["parameters"]["type"] == "SpecificReindexParameters"
    assert messages[0]["parameters"]["ids"] == chosen_record_ids


def test_specific_records_existence_check(deletion_check_initiator, put_records):
    records = [{"id": f"{i:05d}"} for i in range(10)]
    put_records(records)

    with pytest.raises(Exception) as e:
        deletion_check_initiator.specific_records(["not-stored-record"])

    assert "does not exist" in str(e.value)
