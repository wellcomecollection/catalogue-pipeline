from main import lambda_handler
from test_mocks import MockElasticsearchClient
from local_utils import construct_sqs_event


def test_lambda_handler_correctly_indexes_documents():
    index_event = construct_sqs_event("test_bucket", {"prod/test_id_1.xml": False})

    lambda_handler(index_event, None)

    indexed_documents = MockElasticsearchClient.indexed_documents["test_ebsco_index"]
    assert len(indexed_documents.keys()) == 3

    for document in indexed_documents.values():
        assert document["parent.id"] == "test_id_1"
        assert "tag" in document
        assert "position" in document

    assert indexed_documents["test_id_1-001-1"]["data"] == "test_id_1"
    assert indexed_documents["test_id_1-003-2"]["data"] == "EBZ"

    field_856 = indexed_documents["test_id_1-856-3"]
    assert field_856["ind1"] == "4"
    assert field_856["ind2"] == "0"
    assert field_856["position"] == 3

    assert field_856["subfields.code"] == ["3", "z", "u"]

    assert field_856["subfields.content"][0] == "Full text available: 1995 to present."
    assert (
        field_856["subfields.content"][1]
        == "Available in ScienceDirect Subject Collections - Agricultural and Biological Sciences.\n            "
    )
    assert (
        field_856["subfields.content"][2]
        == "https://resolver.ebscohost.com/Redirect/PRL?EPPackageLocationID=841.9579.0&epcustomerid=s7451719"
    )


def test_lambda_handler_deletes_indexed_documents():
    index_event = construct_sqs_event("test_bucket", {"prod/test_id_1.xml": False})

    lambda_handler(index_event, None)

    indexed_documents = MockElasticsearchClient.indexed_documents["test_ebsco_index"]
    assert len(indexed_documents.keys()) == 3

    delete_event = construct_sqs_event("test_bucket", {"prod/test_id_1.xml": True})
    lambda_handler(delete_event, None)

    indexed_documents = MockElasticsearchClient.indexed_documents["test_ebsco_index"]
    assert len(indexed_documents.keys()) == 0


def test_lambda_handler_does_not_delete_incorrect_documents():
    # Index all fields of EBSCO item `test_id_1` and then try to delete all fields of EBSCO item `test_id_2`.
    # No items should be deleted.
    index_event = construct_sqs_event("test_bucket", {"prod/test_id_1.xml": False})

    lambda_handler(index_event, None)

    indexed_documents = MockElasticsearchClient.indexed_documents["test_ebsco_index"]
    assert len(indexed_documents.keys()) == 3

    delete_event = construct_sqs_event("test_bucket", {"prod/test_id_2.xml": True})
    lambda_handler(delete_event, None)

    indexed_documents = MockElasticsearchClient.indexed_documents["test_ebsco_index"]
    assert len(indexed_documents.keys()) == 3


def test_lambda_handler_indexes_and_deletes_multiple_items_at_once():
    # Index 2 items at once
    index_event = construct_sqs_event(
        "test_bucket", {"prod/test_id_1.xml": False, "prod/test_id_2.xml": False}
    )

    lambda_handler(index_event, None)

    indexed_documents = MockElasticsearchClient.indexed_documents["test_ebsco_index"]
    assert len(indexed_documents.keys()) == 5

    # Delete 2 items at once
    delete_event = construct_sqs_event(
        "test_bucket", {"prod/test_id_1.xml": True, "prod/test_id_2.xml": True}
    )

    lambda_handler(delete_event, None)

    indexed_documents = MockElasticsearchClient.indexed_documents["test_ebsco_index"]
    assert len(indexed_documents.keys()) == 0


def test_lambda_handler_indexes_and_deletes_items_at_once():
    # Index an item
    index_event = construct_sqs_event("test_bucket", {"prod/test_id_1.xml": True})
    lambda_handler(index_event, None)

    # Delete the previously indexed item but index a new item at the same time
    index_event = construct_sqs_event(
        "test_bucket", {"prod/test_id_1.xml": True, "prod/test_id_2.xml": False}
    )
    lambda_handler(index_event, None)

    indexed_documents = MockElasticsearchClient.indexed_documents["test_ebsco_index"]
    assert len(indexed_documents.keys()) == 2

    for document in indexed_documents.values():
        assert document["parent.id"] == "test_id_2"
