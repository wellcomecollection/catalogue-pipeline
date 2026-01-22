from __future__ import annotations

import json
import logging
import sys
from argparse import ArgumentParser

import pytest
from pydantic import BaseModel

from tests.mocks import MockStepFunctionsClient
from utils.logger import setup_structlog
from utils.steps import StepFunctionOutput, run_ecs_handler


class ExampleEvent(BaseModel):
    message: str


class ExampleResult(BaseModel):
    status: str


@pytest.fixture(autouse=True)
def configure_structlog() -> None:
    """Ensure structlog is configured to use stdlib logging for caplog capture."""
    setup_structlog()


def test_run_ecs_handler_reports_success(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    event_payload = ExampleEvent(message="hello").model_dump_json()
    token = "token-123"
    parser = ArgumentParser(prog="test-handler")

    handler_calls: list[ExampleEvent] = []

    def handler(event: ExampleEvent) -> ExampleResult:
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
        run_ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
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


def test_run_ecs_handler_reports_failure(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    event_payload = ExampleEvent(message="boom").model_dump_json()
    token = "token-456"
    parser = ArgumentParser(prog="test-handler")

    def handler(event: ExampleEvent) -> ExampleResult:  # noqa: ARG001
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

    with caplog.at_level(logging.ERROR):
        run_ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
        )

    assert MockStepFunctionsClient.task_successes == []
    assert len(MockStepFunctionsClient.task_failures) == 1
    failure = MockStepFunctionsClient.task_failures[0]
    assert failure["taskToken"] == token
    assert failure["error"] == "IngestorLoaderError"
    cause = json.loads(failure["cause"])
    assert cause["message"] == "unexpected kaboom"
    assert cause["type"] == "RuntimeError"

    assert "Sending task failure to Step Functions" in caplog.text


# run_ecs_handler tests


def test_run_ecs_handler_handles_none_result(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    event_payload = ExampleEvent(message="hello").model_dump_json()
    token = "token-789"
    parser = ArgumentParser(prog="test-handler")

    def handler(event: ExampleEvent) -> ExampleResult | None:  # noqa: ARG001
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
        run_ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
        )

    assert MockStepFunctionsClient.task_failures == []
    assert MockStepFunctionsClient.task_successes == [
        {
            "taskToken": token,
            "output": "Result is None",
        }
    ]

    assert "Sending task success to Step Functions" in caplog.text


def test_run_ecs_handler_without_task_token(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    event_payload = ExampleEvent(message="no-token").model_dump_json()
    parser = ArgumentParser(prog="test-handler")

    def handler(event: ExampleEvent) -> ExampleResult:
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
        run_ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
        )

    assert MockStepFunctionsClient.task_successes == []
    assert MockStepFunctionsClient.task_failures == []

    assert "Task result" in caplog.text


def test_run_ecs_handler_without_task_token_none_result(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    event_payload = ExampleEvent(message="no-token").model_dump_json()
    parser = ArgumentParser(prog="test-handler")

    def handler(event: ExampleEvent) -> ExampleResult | None:  # noqa: ARG001
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
        run_ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=ExampleEvent.model_validate_json,
        )

    assert MockStepFunctionsClient.task_successes == []
    assert MockStepFunctionsClient.task_failures == []

    assert "Task result" in caplog.text


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
            "output": "Result is None",
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
    assert failure["error"] == "IngestorLoaderError"
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
