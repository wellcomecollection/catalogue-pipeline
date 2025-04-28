import pytest
from freezegun import freeze_time
from test_mocks import MockRequest

from ingestor_loader import IngestorLoaderLambdaEvent
from ingestor_trigger import IngestorTriggerConfig, IngestorTriggerLambdaEvent, handler
from ingestor_trigger_monitor import IngestorTriggerMonitorLambdaEvent


def get_mock_trigger_event(job_id: str | None) -> IngestorTriggerLambdaEvent:
    return IngestorTriggerLambdaEvent(
        pipeline_date="2025-01-01", index_date="2025-03-01", job_id=job_id
    )


def get_mock_loader_event(
    job_id: str | None, start_offset: int, end_index: int
) -> IngestorLoaderLambdaEvent:
    return IngestorLoaderLambdaEvent(
        **dict(get_mock_trigger_event(job_id)),
        start_offset=start_offset,
        end_index=end_index,
    )


def get_mock_trigger_monitor_event(events: list) -> IngestorTriggerMonitorLambdaEvent:
    return IngestorTriggerMonitorLambdaEvent(
        pipeline_date="2025-01-01",
        index_date="2025-03-01",
        force_pass=False,
        report_results=True,
        events=events,
    )


def build_test_matrix() -> list[tuple]:
    return [
        (
            "job_id set, shard_size > results count",
            get_mock_trigger_event("123"),
            IngestorTriggerConfig(shard_size=100, is_local=False),
            {"results": [{"count": 1}]},
            get_mock_trigger_monitor_event([get_mock_loader_event("123", 0, 1)]),
        ),
        (
            "job_id set, shard_size < results count",
            get_mock_trigger_event("123"),
            IngestorTriggerConfig(shard_size=1, is_local=False),
            {"results": [{"count": 2}]},
            get_mock_trigger_monitor_event(
                [get_mock_loader_event("123", 0, 1), get_mock_loader_event("123", 1, 2)]
            ),
        ),
        (
            "job_id set, shard_size == results count",
            get_mock_trigger_event("123"),
            IngestorTriggerConfig(shard_size=1),
            {"results": [{"count": 1}]},
            get_mock_trigger_monitor_event([get_mock_loader_event("123", 0, 1)]),
        ),
        (
            "job_id set, results count == 0",
            get_mock_trigger_event("123"),
            IngestorTriggerConfig(shard_size=100),
            {"results": [{"count": 0}]},
            get_mock_trigger_monitor_event([]),
        ),
        (
            "job_id set, shard_size unset (default 1k) > results count",
            get_mock_trigger_event("123"),
            IngestorTriggerConfig(),
            {"results": [{"count": 1001}]},
            get_mock_trigger_monitor_event(
                [
                    get_mock_loader_event("123", 0, 1000),
                    get_mock_loader_event("123", 1000, 1001),
                ]
            ),
        ),
        (
            "job_id not set, shard_size > results count",
            get_mock_trigger_event(None),
            IngestorTriggerConfig(shard_size=100),
            {"results": [{"count": 1}]},
            get_mock_trigger_monitor_event(
                [get_mock_loader_event("20120101T0000", 0, 1)]
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
