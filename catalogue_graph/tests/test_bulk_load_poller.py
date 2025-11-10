import pytest

from bulk_load_poller import lambda_handler
from models.neptune_bulk_loader import (
    BulkLoadErrors,
    BulkLoadFeed,
    BulkLoadStatusResponse,
)
from tests.mocks import MockCloudwatchClient, MockRequest

LOAD_ID = "123"
BULK_LOADER_S3_PREFIX = "s3://wellcomecollection-catalogue-graph/graph_bulk_loader"
PIPELINE_DATE = "2020-12-12"
TRANSFORMER_TYPE = "catalogue_concepts"
ENTITY_TYPE = "nodes"

S3_PATH = f"{PIPELINE_DATE}/windows/20251022T0800-20251022T0815/{TRANSFORMER_TYPE}__{ENTITY_TYPE}.csv"


def _get_mock_metric(name: str, value: int) -> dict:
    dimensions = {
        "pipeline_date": PIPELINE_DATE,
        "transformer_type": TRANSFORMER_TYPE,
        "entity_type": ENTITY_TYPE,
    }

    return {
        "namespace": "catalogue_graph_pipeline",
        "metric_name": name,
        "value": value,
        "dimensions": dimensions,
    }


def add_mock_status_response(
    status: str,
    record_count: int = 0,
    duplicate_count: int = 0,
    parsing_error_count: int = 0,
    data_type_error_count: int = 0,
    insert_error_count: int = 0,
) -> None:
    mock_status = BulkLoadStatusResponse(
        overall_status=BulkLoadFeed(
            full_uri=f"{BULK_LOADER_S3_PREFIX}/{S3_PATH}",
            run_number=123,
            retry_number=0,
            status=status,
            total_time_spent=134,
            start_time=4532,
            total_records=record_count,
            total_duplicates=duplicate_count,
            parsing_errors=parsing_error_count,
            datatype_mismatch_errors=data_type_error_count,
            insert_errors=insert_error_count,
        ),
        errors=BulkLoadErrors(
            start_index=0, end_index=0, load_id=LOAD_ID, error_logs=[]
        ),
    )

    MockRequest.mock_response(
        method="GET",
        url=f"https://test-host.com:8182/loader?loadId={LOAD_ID}&errors=TRUE&details=TRUE",
        json_data={"payload": mock_status.model_dump()},
    )


@pytest.mark.parametrize(
    "status", ["LOAD_IN_PROGRESS", "LOAD_NOT_STARTED", "LOAD_IN_QUEUE"]
)
def test_bulk_load_in_progress(status: str) -> None:
    add_mock_status_response(status)

    event = {"load_id": LOAD_ID}
    response = lambda_handler(event, None)
    assert response == {
        "load_id": LOAD_ID,
        "insert_error_threshold": 0.0001,
        "status": "IN_PROGRESS",
    }

    assert MockCloudwatchClient.metrics_reported == []


def test_bulk_load_succeeded() -> None:
    record_count = 25
    duplicate_count = 24
    add_mock_status_response(
        "LOAD_COMPLETED", record_count=record_count, duplicate_count=duplicate_count
    )

    event = {"load_id": LOAD_ID}
    response = lambda_handler(event, None)
    assert response == {
        "load_id": LOAD_ID,
        "insert_error_threshold": 0.0001,
        "status": "SUCCEEDED",
    }

    assert MockCloudwatchClient.metrics_reported == [
        _get_mock_metric("record_count", record_count),
        _get_mock_metric("duplicate_count", duplicate_count),
        _get_mock_metric("new_count", record_count - duplicate_count),
        _get_mock_metric("parsing_error_count", 0),
        _get_mock_metric("data_type_error_count", 0),
        _get_mock_metric("insert_error_count", 0),
    ]


def test_bulk_load_failed() -> None:
    record_count = 25
    insert_error_count = 5
    add_mock_status_response(
        "LOAD_FAILED",
        record_count=record_count,
        insert_error_count=insert_error_count,
    )

    event = {"load_id": LOAD_ID}
    with pytest.raises(Exception, match="Load failed."):
        lambda_handler(event, None)

    assert MockCloudwatchClient.metrics_reported == [
        _get_mock_metric("record_count", record_count),
        _get_mock_metric("duplicate_count", 0),
        _get_mock_metric("new_count", record_count),
        _get_mock_metric("parsing_error_count", 0),
        _get_mock_metric("data_type_error_count", 0),
        _get_mock_metric("insert_error_count", insert_error_count),
    ]


def test_bulk_load_failed_below_error_threshold() -> None:
    record_count = 100000
    insert_error_count = 5
    add_mock_status_response(
        "LOAD_FAILED",
        record_count=record_count,
        insert_error_count=insert_error_count,
    )

    event = {"load_id": LOAD_ID}
    response = lambda_handler(event, None)
    assert response == {
        "load_id": LOAD_ID,
        "insert_error_threshold": 0.0001,
        "status": "SUCCEEDED",
    }
