import json
from argparse import ArgumentParser
from collections.abc import Callable
from datetime import datetime
from typing import Concatenate, ParamSpec, Protocol, TypeVar

import boto3
from pydantic import BaseModel

Params = ParamSpec("Params")
EventModel = TypeVar("EventModel", bound=BaseModel)
ResultModel = TypeVar("ResultModel", bound=BaseModel)
HandlerFunction = Callable[Concatenate[EventModel, Params], ResultModel | None]
EventValidator = Callable[[str], EventModel]


class StepFunctionClient(Protocol):
    def send_task_success(self, taskToken: str, output: str) -> None: ...

    def send_task_failure(self, taskToken: str, error: str, cause: str) -> None: ...


class StepFunctionOutput:
    def __init__(
        self, task_token: str | None, stepfunctions_client: StepFunctionClient | None
    ) -> None:
        self.task_token = task_token
        self.stepfunctions_client = stepfunctions_client or boto3.client(
            "stepfunctions"
        )
        self.can_report = task_token is not None and stepfunctions_client is not None

    def _dump_result(self, result: ResultModel | None) -> str:
        if result is not None:
            return result.model_dump_json()
        return "Result is None"

    def send_success(self, result: ResultModel | None) -> None:
        output = self._dump_result(result)

        if self.can_report:
            assert self.task_token is not None
            stepfunctions_client = boto3.client("stepfunctions")
            print("Sending task success to Step Functions.")
            stepfunctions_client.send_task_success(
                taskToken=self.task_token,
                output=output,
            )
        else:
            print(f"Result: {output}")

    def send_failure(self, error: Exception) -> None:
        error_output = json.dumps(
            {
                "message": str(error),
                "type": type(error).__name__,
                "stack": str(error.__traceback__),
            }
        )

        if self.can_report:
            assert self.task_token is not None
            print(f"Sending task failure to Step Functions: {error_output}")
            self.stepfunctions_client.send_task_failure(
                taskToken=self.task_token,
                error="IngestorLoaderError",
                cause=error_output,
            )
        else:
            print(f"Error: {error_output}")


def run_ecs_handler(
    arg_parser: ArgumentParser,
    handler: HandlerFunction,
    event_validator: EventValidator,
    *handler_args: Params.args,  # type: ignore[valid-type]
    **handler_kwargs: Params.kwargs,  # type: ignore[valid-type]
) -> None:
    arg_parser.add_argument(
        "--event",
        type=event_validator,
        help="Raw event in JSON format.",
        required=True,
    )
    arg_parser.add_argument(
        "--task-token",
        type=str,
        help="The Step Functions task token for reporting success or failure.",
        required=False,
    )

    ecs_args = arg_parser.parse_args()
    task_token = ecs_args.task_token
    event = ecs_args.event

    stepfunctions_client = boto3.client("stepfunctions") if task_token else None
    step_output = StepFunctionOutput(task_token, stepfunctions_client)

    try:
        result = handler(event=event, *handler_args, **handler_kwargs)  # noqa: B026
        step_output.send_success(result)
    except Exception as exc:
        step_output.send_failure(exc)


def create_job_id() -> str:
    """Generate a job_id based on the current time using an iso8601 format like 20210701T1300"""
    return datetime.now().strftime("%Y%m%dT%H%M")
