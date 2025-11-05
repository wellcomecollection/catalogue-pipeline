import json
from argparse import ArgumentParser
from collections.abc import Callable
from datetime import datetime
from typing import Concatenate, ParamSpec, TypeVar

import boto3
from pydantic import BaseModel

Params = ParamSpec("Params")
EventModel = TypeVar("EventModel", bound=BaseModel)
ResultModel = TypeVar("ResultModel", bound=BaseModel)
HandlerFunction = Callable[Concatenate[EventModel, Params], ResultModel]
EventValidator = Callable[[str], EventModel]


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
    event = ecs_args.event
    task_token = ecs_args.task_token

    if task_token:
        print(
            "Received TASK_TOKEN in ECS arguments, will report back to Step Functions."
        )
        stepfunctions_client = boto3.client("stepfunctions")

        try:
            result = handler(event=event, *handler_args, **handler_kwargs)  # noqa: B026
            output = result.model_dump_json()
        except Exception as exc:
            error_output = json.dumps({"error": str(exc)})
            print(f"Sending task failure to Step Functions: {error_output}")
            stepfunctions_client.send_task_failure(
                taskToken=task_token,
                error="IngestorLoaderError",
                cause=error_output,
            )
        else:
            print("Sending task success to Step Functions.")
            stepfunctions_client.send_task_success(
                taskToken=task_token,
                output=output,
            )
    else:
        result = handler(event=event, *handler_args, **handler_kwargs)  # noqa: B026
        output = result.model_dump_json()
        print("No TASK_TOKEN provided, skipping send_task_success.")
        print(f"Result: {output}")


def create_job_id() -> str:
    """Generate a job_id based on the current time using an iso8601 format like 20210701T1300"""
    return datetime.now().strftime("%Y%m%dT%H%M")
