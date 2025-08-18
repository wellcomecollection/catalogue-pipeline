import pytest
from freezegun import freeze_time
from test_mocks import (
    MockRequest,
    get_mock_ingestor_loader_event,
    get_mock_ingestor_trigger_event,
)

from ingestor.models.step_events import (
    IngestorTriggerLambdaEvent,
    IngestorTriggerMonitorLambdaEvent,
)
from ingestor.steps.ingestor_trigger import (
    IngestorTriggerConfig,
    handler,
    lambda_handler,
)


def get_mock_trigger_monitor_event(events: list) -> IngestorTriggerMonitorLambdaEvent:
    job_id = events[0].job_id if len(events) > 0 else "123"
    return IngestorTriggerMonitorLambdaEvent(
        ingestor_type="concepts",
        pipeline_date="2025-01-01",
        index_date="2025-03-01",
        job_id=job_id,
        force_pass=False,
        report_results=True,
        events=events,
    )


def build_test_matrix() -> list[tuple]:
    return [
        (
            "job_id set, shard_size > results count",
            get_mock_ingestor_trigger_event("123"),
            IngestorTriggerConfig(shard_size=100, is_local=False),
            1,
            get_mock_trigger_monitor_event(
                [get_mock_ingestor_loader_event("123", 0, 1)]
            ),
        ),
        (
            "job_id set, shard_size < results count",
            get_mock_ingestor_trigger_event("123"),
            IngestorTriggerConfig(shard_size=1, is_local=False),
            2,
            get_mock_trigger_monitor_event(
                [
                    get_mock_ingestor_loader_event("123", 0, 1),
                    get_mock_ingestor_loader_event("123", 1, 2),
                ]
            ),
        ),
        (
            "job_id set, shard_size == results count",
            get_mock_ingestor_trigger_event("123"),
            IngestorTriggerConfig(shard_size=1),
            1,
            get_mock_trigger_monitor_event(
                [get_mock_ingestor_loader_event("123", 0, 1)]
            ),
        ),
        (
            "job_id set, results count == 0",
            get_mock_ingestor_trigger_event("123"),
            IngestorTriggerConfig(shard_size=100),
            0,
            get_mock_trigger_monitor_event([]),
        ),
        (
            "job_id set, shard_size unset (default 1k) > results count",
            get_mock_ingestor_trigger_event("123"),
            IngestorTriggerConfig(),
            10001,
            get_mock_trigger_monitor_event(
                [
                    get_mock_ingestor_loader_event("123", 0, 10000),
                    get_mock_ingestor_loader_event("123", 10000, 10001),
                ]
            ),
        ),
    ]


def mock_neptune_response(count: int) -> None:
    MockRequest.mock_response(
        method="POST",
        url="https://test-host.com:8182/openCypher",
        json_data={"results": [{"count": count}]},
    )


def get_test_id(argvalue: str) -> str:
    return argvalue


@pytest.mark.parametrize(
    "description,event,config,neptune_result_count,expected_output",
    build_test_matrix(),
    ids=get_test_id,
)
def test_ingestor_trigger(
    description: str,
    event: IngestorTriggerLambdaEvent,
    config: IngestorTriggerConfig,
    neptune_result_count: int,
    expected_output: IngestorTriggerMonitorLambdaEvent,
) -> None:
    mock_neptune_response(neptune_result_count)

    result = handler(event, config)

    assert result == expected_output
    assert len(MockRequest.calls) == 1

    request = MockRequest.calls[0]

    assert request["method"] == "POST"
    assert request["url"] == "https://test-host.com:8182/openCypher"


@freeze_time("2012-01-01")
def test_job_id_generation() -> None:
    event = {
        "ingestor_type": "concepts",
        "pipeline_date": "2025-01-01",
        "index_date": "2025-03-01",
    }

    mock_neptune_response(1)
    result = lambda_handler(event, None)
    assert result["job_id"] == "20120101T0000"
