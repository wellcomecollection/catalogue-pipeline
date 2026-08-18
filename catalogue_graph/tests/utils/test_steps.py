from __future__ import annotations

import json
import logging
import sys
import threading
import time
from argparse import ArgumentParser

import pytest
from pydantic import BaseModel, ValidationError

from tests.mocks import MockStepFunctionsClient
from utils.logger import ExecutionContext, setup_structlog
from utils.steps import (
    MAX_CONSECUTIVE_HEARTBEAT_FAILURES,
    StepFunctionOutput,
    ecs_handler,
    task_heartbeat,
)


class ExampleEvent(BaseModel):
    message: str


class ExampleResult(BaseModel):
    status: str


@pytest.fixture(autouse=True)
def configure_structlog() -> None:
    """Ensure structlog is configured to use stdlib logging for caplog capture."""
    setup_structlog()


def test_ecs_handler_reports_success(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    event_payload = ExampleEvent(message="hello").model_dump_json()
    token = "token-123"
    parser = ArgumentParser(prog="test-handler")

    handler_calls: list[ExampleEvent] = []

    def handler(
        event: ExampleEvent,
        execution_context: ExecutionContext | None = None,  # noqa: ARG001
    ) -> ExampleResult:
        handler_calls.append(event)
        return ExampleResult(status=f"processed-{event.message}")

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "prog",
            "--event",
            event_payload,
            "--task-token",
            token,
        ],
    )

    with caplog.at_level(logging.INFO):
        ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
            pipeline_step="test_step",
        )

    assert handler_calls == [ExampleEvent(message="hello")]
    assert MockStepFunctionsClient.task_failures == []
    assert MockStepFunctionsClient.task_successes == [
        {
            "taskToken": token,
            "output": ExampleResult(status="processed-hello").model_dump_json(),
        }
    ]

    assert "Sending task success to Step Functions" in caplog.text


def test_ecs_handler_reports_failure(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    event_payload = ExampleEvent(message="boom").model_dump_json()
    token = "token-456"
    parser = ArgumentParser(prog="test-handler")

    def handler(
        event: ExampleEvent,  # noqa: ARG001
        execution_context: ExecutionContext | None = None,  # noqa: ARG001
    ) -> ExampleResult:
        raise RuntimeError("unexpected kaboom")

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "prog",
            "--event",
            event_payload,
            "--task-token",
            token,
        ],
    )

    with (
        caplog.at_level(logging.ERROR),
        pytest.raises(RuntimeError, match="unexpected kaboom"),
    ):
        ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
            pipeline_step="test_step",
        )

    assert MockStepFunctionsClient.task_successes == []
    assert len(MockStepFunctionsClient.task_failures) == 1
    failure = MockStepFunctionsClient.task_failures[0]
    assert failure["taskToken"] == token
    assert failure["error"] == "RuntimeError"
    cause = json.loads(failure["cause"])
    assert cause["message"] == "unexpected kaboom"
    assert cause["type"] == "RuntimeError"

    assert "Sending task failure to Step Functions" in caplog.text


def test_ecs_handler_reports_failure_on_invalid_event(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    invalid_payload = '{"not_a_valid_field": 123}'
    token = "token-invalid"
    parser = ArgumentParser(prog="test-handler")

    def handler(
        event: ExampleEvent,  # noqa: ARG001
        execution_context: ExecutionContext | None = None,  # noqa: ARG001
    ) -> ExampleResult:
        raise AssertionError("handler should not be called")

    monkeypatch.setattr(
        sys,
        "argv",
        ["prog", "--event", invalid_payload, "--task-token", token],
    )

    with (
        caplog.at_level(logging.ERROR),
        pytest.raises(ValidationError),
    ):
        ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
            pipeline_step="test_step",
        )

    assert MockStepFunctionsClient.task_successes == []
    assert len(MockStepFunctionsClient.task_failures) == 1
    failure = MockStepFunctionsClient.task_failures[0]
    assert failure["taskToken"] == token
    assert "Sending task failure to Step Functions" in caplog.text


# ecs_handler tests


def test_ecs_handler_handles_none_result(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    event_payload = ExampleEvent(message="hello").model_dump_json()
    token = "token-789"
    parser = ArgumentParser(prog="test-handler")

    def handler(
        event: ExampleEvent,  # noqa: ARG001
        execution_context: ExecutionContext | None = None,  # noqa: ARG001
    ) -> ExampleResult | None:
        return None

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "prog",
            "--event",
            event_payload,
            "--task-token",
            token,
        ],
    )

    with caplog.at_level(logging.INFO):
        ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
            pipeline_step="test_step",
        )

    assert MockStepFunctionsClient.task_failures == []
    assert MockStepFunctionsClient.task_successes == [
        {
            "taskToken": token,
            "output": "{}",
        }
    ]

    assert "Sending task success to Step Functions" in caplog.text


def test_ecs_handler_without_task_token(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    event_payload = ExampleEvent(message="no-token").model_dump_json()
    parser = ArgumentParser(prog="test-handler")

    def handler(
        event: ExampleEvent,
        execution_context: ExecutionContext | None = None,  # noqa: ARG001
    ) -> ExampleResult:
        return ExampleResult(status=f"processed-{event.message}")

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "prog",
            "--event",
            event_payload,
        ],
    )

    with caplog.at_level(logging.INFO):
        ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
            pipeline_step="test_step",
        )

    assert MockStepFunctionsClient.task_successes == []
    assert MockStepFunctionsClient.task_failures == []

    assert "Task result" in caplog.text


def test_ecs_handler_without_task_token_none_result(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    event_payload = ExampleEvent(message="no-token").model_dump_json()
    parser = ArgumentParser(prog="test-handler")

    def handler(
        event: ExampleEvent,  # noqa: ARG001
        execution_context: ExecutionContext | None = None,  # noqa: ARG001
    ) -> ExampleResult | None:
        return None

    monkeypatch.setattr(
        sys,
        "argv",
        [
            "prog",
            "--event",
            event_payload,
        ],
    )

    with caplog.at_level(logging.INFO):
        ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
            pipeline_step="test_step",
        )

    assert MockStepFunctionsClient.task_successes == []
    assert MockStepFunctionsClient.task_failures == []

    assert "Task result" in caplog.text


# task_heartbeat tests


def test_task_heartbeat_reports_while_running() -> None:
    with task_heartbeat(MockStepFunctionsClient(), "token-beat", interval_seconds=0.01):
        for _ in range(200):
            if MockStepFunctionsClient.task_heartbeats:
                break
            time.sleep(0.01)

    assert MockStepFunctionsClient.task_heartbeats[0] == "token-beat"

    # The thread is stopped on exit, so no further heartbeats arrive.
    sent = len(MockStepFunctionsClient.task_heartbeats)
    time.sleep(0.05)
    assert len(MockStepFunctionsClient.task_heartbeats) == sent


class FlakyHeartbeatClient:
    """Fails the first `failures` heartbeats, then succeeds."""

    def __init__(self, failures: int) -> None:
        self.failures = failures
        self.calls = 0

    def send_task_success(self, taskToken: str, output: str) -> None: ...  # noqa: N803

    def send_task_failure(self, taskToken: str, error: str, cause: str) -> None: ...  # noqa: N803

    def send_task_heartbeat(self, taskToken: str) -> None:  # noqa: N803
        self.calls += 1
        if self.calls <= self.failures:
            raise RuntimeError("blip")


def _wait_for_calls(client: FlakyHeartbeatClient, target: int) -> None:
    for _ in range(200):
        if client.calls >= target:
            return
        time.sleep(0.01)


def test_task_heartbeat_absorbs_a_transient_failure() -> None:
    client = FlakyHeartbeatClient(failures=1)
    target = MAX_CONSECUTIVE_HEARTBEAT_FAILURES + 2

    with task_heartbeat(client, "token-flaky", interval_seconds=0.01):
        _wait_for_calls(client, target)

    assert client.calls >= target


def test_task_heartbeat_gives_up_after_consecutive_failures(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client = FlakyHeartbeatClient(failures=1000)
    # The thread dies by design here; keep its traceback out of the test output.
    monkeypatch.setattr(threading, "excepthook", lambda args: None)

    with task_heartbeat(client, "token-broken", interval_seconds=0.01):
        _wait_for_calls(client, MAX_CONSECUTIVE_HEARTBEAT_FAILURES)
        time.sleep(0.1)

    assert client.calls == MAX_CONSECUTIVE_HEARTBEAT_FAILURES


def test_task_heartbeat_without_token_does_nothing() -> None:
    with task_heartbeat(None, None, interval_seconds=0.01):
        time.sleep(0.05)

    assert MockStepFunctionsClient.task_heartbeats == []


# StepFunctionOutput tests


def test_step_function_output_send_success_reports() -> None:
    output = StepFunctionOutput("token-123", MockStepFunctionsClient())

    output.send_success(ExampleResult(status="ok"))

    assert MockStepFunctionsClient.task_failures == []
    assert MockStepFunctionsClient.task_successes == [
        {
            "taskToken": "token-123",
            "output": ExampleResult(status="ok").model_dump_json(),
        }
    ]


def test_step_function_output_send_success_without_token_logs(
    caplog: pytest.LogCaptureFixture,
) -> None:
    output = StepFunctionOutput(None, None)

    with caplog.at_level(logging.INFO):
        output.send_success(ExampleResult(status="ok"))

    assert MockStepFunctionsClient.task_successes == []
    assert "Task result" in caplog.text


def test_step_function_output_send_success_none_result_records() -> None:
    output = StepFunctionOutput("token-456", MockStepFunctionsClient())

    output.send_success(None)

    assert MockStepFunctionsClient.task_successes == [
        {
            "taskToken": "token-456",
            "output": "{}",
        }
    ]


def test_step_function_output_send_failure_reports(
    caplog: pytest.LogCaptureFixture,
) -> None:
    output = StepFunctionOutput("token-555", MockStepFunctionsClient())

    with caplog.at_level(logging.ERROR):
        output.send_failure(RuntimeError("boom"))

    assert MockStepFunctionsClient.task_successes == []
    assert len(MockStepFunctionsClient.task_failures) == 1
    failure = MockStepFunctionsClient.task_failures[0]
    assert failure["taskToken"] == "token-555"
    assert failure["error"] == "RuntimeError"
    cause = json.loads(failure["cause"])
    assert cause["message"] == "boom"
    assert cause["type"] == "RuntimeError"
    assert "Sending task failure to Step Functions" in caplog.text


def test_step_function_output_send_failure_without_token_logs(
    caplog: pytest.LogCaptureFixture,
) -> None:
    output = StepFunctionOutput(None, None)

    with caplog.at_level(logging.ERROR):
        output.send_failure(RuntimeError("boom"))

    assert MockStepFunctionsClient.task_failures == []
    assert "Task error" in caplog.text


def test_step_function_output_send_failure_names_the_exception_type() -> None:
    # The name is what Step Functions matches Retry/Catch on, so distinct
    # exception types must not collapse to one string.
    output = StepFunctionOutput("token-666", MockStepFunctionsClient())

    output.send_failure(ValueError("bad input"))
    output.send_failure(TimeoutError("too slow"))

    errors = [f["error"] for f in MockStepFunctionsClient.task_failures]
    assert errors == ["ValueError", "TimeoutError"]
