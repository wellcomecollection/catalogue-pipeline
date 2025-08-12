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
from ingestor.steps.ingestor_trigger import IngestorTriggerConfig, handler


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
            {"results": [{"count": 1}]},
            get_mock_trigger_monitor_event(
                [get_mock_ingestor_loader_event("123", 0, 1)]
            ),
        ),
        (
            "job_id set, shard_size < results count",
            get_mock_ingestor_trigger_event("123"),
            IngestorTriggerConfig(shard_size=1, is_local=False),
            {"results": [{"count": 2}]},
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
            {"results": [{"count": 1}]},
            get_mock_trigger_monitor_event(
                [get_mock_ingestor_loader_event("123", 0, 1)]
            ),
        ),
        (
            "job_id set, results count == 0",
            get_mock_ingestor_trigger_event("123"),
            IngestorTriggerConfig(shard_size=100),
            {"results": [{"count": 0}]},
            get_mock_trigger_monitor_event([]),
        ),
        (
            "job_id set, shard_size unset (default 1k) > results count",
            get_mock_ingestor_trigger_event("123"),
            IngestorTriggerConfig(),
            {"results": [{"count": 10001}]},
            get_mock_trigger_monitor_event(
                [
                    get_mock_ingestor_loader_event("123", 0, 10000),
                    get_mock_ingestor_loader_event("123", 10000, 10001),
                ]
            ),
        ),
        (
            "job_id not set, shard_size > results count",
            get_mock_ingestor_trigger_event(None),
            IngestorTriggerConfig(shard_size=100),
            {"results": [{"count": 1}]},
            get_mock_trigger_monitor_event(
                [get_mock_ingestor_loader_event("20120101T0000", 0, 1)]
            ),
        ),
    ]


def get_test_id(argvalue: str) -> str:
    return argvalue


@freeze_time("2012-01-01")
@pytest.mark.parametrize(
    "description,event,config,neptune_response,expected_output",
    build_test_matrix(),
    ids=get_test_id,
)
def test_ingestor_trigger(
    description: str,
    event: IngestorTriggerLambdaEvent,
    config: IngestorTriggerConfig,
    neptune_response: dict,
    expected_output: IngestorTriggerMonitorLambdaEvent,
) -> None:
    MockRequest.mock_responses(
        [
            {
                "method": "POST",
                "url": "https://test-host.com:8182/openCypher",
                "status_code": 200,
                "json_data": neptune_response,
                "content_bytes": None,
                "params": None,
            }
        ]
    )

    result = handler(event, config)

    assert result == expected_output
    assert len(MockRequest.calls) == 1

    request = MockRequest.calls[0]

    assert request["method"] == "POST"
    assert request["url"] == "https://test-host.com:8182/openCypher"
