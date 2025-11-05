from __future__ import annotations

import json
import sys
from argparse import ArgumentParser

import pytest
from pydantic import BaseModel

from tests.mocks import MockStepFunctionsClient
from utils.steps import run_ecs_handler


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
    assert MockStepFunctionsClient.task_failures == [
        {
            "taskToken": token,
            "error": "IngestorLoaderError",
            "cause": json.dumps({"error": "unexpected kaboom"}),
        }
    ]

    captured = capsys.readouterr()
    assert "Sending task failure to Step Functions" in captured.out


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
    assert "No TASK_TOKEN provided, skipping send_task_success." in captured.out
    expected_output = ExampleResult(status="processed-no-token").model_dump_json()
    assert f"Result: {expected_output}" in captured.out
