from __future__ import annotations

import json
import sys
from argparse import ArgumentParser

import pytest
from pydantic import BaseModel

from tests.mocks import MockStepFunctionsClient
from utils.steps import StepFunctionOutput, run_ecs_handler


class ExampleEvent(BaseModel):
    message: str


class ExampleResult(BaseModel):
    status: str


def test_run_ecs_handler_reports_success(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
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

    captured = capsys.readouterr()
    assert "Sending task success to Step Functions." in captured.out


def test_run_ecs_handler_reports_failure(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
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

    captured = capsys.readouterr()
    assert "Sending task failure to Step Functions" in captured.out


# run_ecs_handler tests


def test_run_ecs_handler_handles_none_result(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
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

    captured = capsys.readouterr()
    assert "Sending task success to Step Functions." in captured.out


def test_run_ecs_handler_without_task_token(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
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

    run_ecs_handler(
        arg_parser=parser,
        handler=handler,
        event_validator=ExampleEvent.model_validate_json,
    )

    assert MockStepFunctionsClient.task_successes == []
    assert MockStepFunctionsClient.task_failures == []

    captured = capsys.readouterr()
    expected_output = ExampleResult(status="processed-no-token").model_dump_json()
    assert f"Result: {expected_output}" in captured.out


def test_run_ecs_handler_without_task_token_none_result(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
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

    run_ecs_handler(
        arg_parser=parser,
        handler=handler,
        event_validator=ExampleEvent.model_validate_json,
    )

    assert MockStepFunctionsClient.task_successes == []
    assert MockStepFunctionsClient.task_failures == []

    captured = capsys.readouterr()
    assert "Result: Result is None" in captured.out


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


def test_step_function_output_send_success_without_token_prints(
    capsys: pytest.CaptureFixture[str],
) -> None:
    output = StepFunctionOutput(None, None)

    output.send_success(ExampleResult(status="ok"))

    assert MockStepFunctionsClient.task_successes == []
    captured = capsys.readouterr()
    expected = ExampleResult(status="ok").model_dump_json()
    assert f"Result: {expected}" in captured.out


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
    capsys: pytest.CaptureFixture[str],
) -> None:
    output = StepFunctionOutput("token-555", MockStepFunctionsClient())

    output.send_failure(RuntimeError("boom"))

    assert MockStepFunctionsClient.task_successes == []
    assert len(MockStepFunctionsClient.task_failures) == 1
    failure = MockStepFunctionsClient.task_failures[0]
    assert failure["taskToken"] == "token-555"
    assert failure["error"] == "IngestorLoaderError"
    cause = json.loads(failure["cause"])
    assert cause["message"] == "boom"
    assert cause["type"] == "RuntimeError"
    captured = capsys.readouterr()
    assert "Sending task failure to Step Functions" in captured.out


def test_step_function_output_send_failure_without_token_prints(
    capsys: pytest.CaptureFixture[str],
) -> None:
    output = StepFunctionOutput(None, None)

    output.send_failure(RuntimeError("boom"))

    assert MockStepFunctionsClient.task_failures == []
    captured = capsys.readouterr()
    assert "Error: {" in captured.out
