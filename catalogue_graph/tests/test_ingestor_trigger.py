import pytest
from freezegun import freeze_time
from ingestor_loader import IngestorLoaderLambdaEvent
from ingestor_trigger import IngestorTriggerConfig, IngestorTriggerLambdaEvent, handler
from ingestor_trigger_monitor import IngestorTriggerMonitorLambdaEvent
from test_mocks import MockRequest


def build_test_matrix() -> list[tuple]:
    return [
        (
            "job_id set, shard_size > results count",
            IngestorTriggerLambdaEvent(pipeline_date="2025-01-01", job_id="123"),
            IngestorTriggerConfig(shard_size=100, is_local=False),
            {"results": [{"count": 1}]},
            IngestorTriggerMonitorLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                force_pass=False,
                report_results=True,
                events=[
                    IngestorLoaderLambdaEvent(
                        pipeline_date="2025-01-01",
                        index_date="2025-01-01",
                        job_id="123",
                        start_offset=0,
                        end_index=1,
                    )
                ],
            ),
        ),
        (
            "job_id set, shard_size < results count",
            IngestorTriggerLambdaEvent(pipeline_date="2025-01-01", job_id="123"),
            IngestorTriggerConfig(shard_size=1, is_local=False),
            {"results": [{"count": 2}]},
            IngestorTriggerMonitorLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                force_pass=False,
                report_results=True,
                events=[
                    IngestorLoaderLambdaEvent(
                        pipeline_date="2025-01-01",
                        index_date="2025-01-01",
                        job_id="123",
                        start_offset=0,
                        end_index=1,
                    ),
                    IngestorLoaderLambdaEvent(
                        pipeline_date="2025-01-01",
                        index_date="2025-01-01",
                        job_id="123",
                        start_offset=1,
                        end_index=2,
                    ),
                ],
            ),
        ),
        (
            "job_id set, shard_size == results count",
            IngestorTriggerLambdaEvent(pipeline_date="2025-01-01", job_id="123"),
            IngestorTriggerConfig(shard_size=1),
            {"results": [{"count": 1}]},
            IngestorTriggerMonitorLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                force_pass=False,
                report_results=True,
                events=[
                    IngestorLoaderLambdaEvent(
                        pipeline_date="2025-01-01",
                        index_date="2025-01-01",
                        job_id="123",
                        start_offset=0,
                        end_index=1,
                    )
                ],
            ),
        ),
        (
            "job_id set, results count == 0",
            IngestorTriggerLambdaEvent(pipeline_date="2025-01-01", job_id="123"),
            IngestorTriggerConfig(shard_size=100),
            {"pipeline_date": "2025-01-01", "results": [{"count": 0}]},
            IngestorTriggerMonitorLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                force_pass=False,
                report_results=True,
                events=[],
            ),
        ),
        (
            "job_id set, shard_size unset (default 1k) > results count",
            IngestorTriggerLambdaEvent(pipeline_date="2025-01-01", job_id="123"),
            IngestorTriggerConfig(),
            {"pipeline_date": "2025-01-01", "results": [{"count": 1001}]},
            IngestorTriggerMonitorLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                force_pass=False,
                report_results=True,
                events=[
                    IngestorLoaderLambdaEvent(
                        pipeline_date="2025-01-01",
                        index_date="2025-01-01",
                        job_id="123",
                        start_offset=0,
                        end_index=1000,
                    ),
                    IngestorLoaderLambdaEvent(
                        pipeline_date="2025-01-01",
                        index_date="2025-01-01",
                        job_id="123",
                        start_offset=1000,
                        end_index=1001,
                    ),
                ],
            ),
        ),
        (
            "job_id not set, shard_size > results count",
            IngestorTriggerLambdaEvent(pipeline_date="2025-01-01", job_id=None),
            IngestorTriggerConfig(shard_size=100),
            {"results": [{"count": 1}]},
            IngestorTriggerMonitorLambdaEvent(
                pipeline_date="2025-01-01",
                index_date="2025-01-01",
                force_pass=False,
                report_results=True,
                events=[
                    IngestorLoaderLambdaEvent(
                        pipeline_date="2025-01-01",
                        index_date="2025-01-01",
                        job_id="20120101T0000",
                        start_offset=0,
                        end_index=1,
                    )
                ],
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
