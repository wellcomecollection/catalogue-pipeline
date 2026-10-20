"""Entry points for the harness: the Lambda handler and a laptop CLI.

Run locally:
    AWS_PROFILE=platform-developer uv run python -m es_bench.handler run --event-file EVENT.json
Fan out the same event over N concurrent Lambda invocations (the merger shape):
    AWS_PROFILE=platform-developer uv run python -m es_bench.handler invoke \
        --function-name catalogue-2026-10-14-serverless-es-bench --event-file EVENT.json --parallel 30
"""

import argparse
import json
import sys
import typing
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any

import boto3
import structlog
from botocore.config import Config

from es_bench.bench import (
    count_index,
    get_client,
    load_slice,
    ping,
    scroll_index,
    timed,
    write_matrix,
)
from es_bench.models import BenchEvent
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def run(event: BenchEvent) -> dict[str, Any]:
    target_client = get_client(event.target)
    result: dict[str, Any]
    if event.mode == "write_matrix":
        if event.source is None or event.sample is None or event.write is None:
            raise ValueError("write_matrix needs source, sample and write")
        source_client = get_client(event.source)
        sample, write = event.sample, event.write
        result = timed(
            "write_matrix",
            lambda: write_matrix(source_client, target_client, sample, write),
        )
    elif event.mode == "scroll":
        if event.scroll is None:
            raise ValueError("scroll needs scroll")
        scroll = event.scroll
        result = timed("scroll", lambda: scroll_index(target_client, scroll))
    elif event.mode == "load":
        if event.source is None or event.load is None:
            raise ValueError("load needs source and load")
        source_client = get_client(event.source)
        load = event.load
        version = event.write.version if event.write and event.write.version else 1
        result = timed(
            "load", lambda: load_slice(source_client, target_client, load, version)
        )
    elif event.mode == "count":
        if event.count is None:
            raise ValueError("count needs count")
        count = event.count
        result = timed("count", lambda: count_index(target_client, count))
    else:
        ping_spec = event.ping
        result = timed("ping", lambda: ping(target_client, ping_spec))
    result["mode"] = event.mode
    result["target"] = event.target.name
    return result


def lambda_handler(event: dict[str, Any], context: typing.Any) -> dict[str, Any]:
    setup_logging(
        ExecutionContext(trace_id=get_trace_id(context), pipeline_step="es_bench")
    )
    return run(BenchEvent.model_validate(event))


def _load_event(path: str) -> dict[str, Any]:
    return typing.cast(dict[str, Any], json.loads(Path(path).read_text()))


def invoke_many(
    function_name: str, event: dict[str, Any], parallel: int
) -> list[dict[str, Any]]:
    """Invoke the harness Lambda `parallel` times at once with the same event."""
    # A matrix invocation can run for most of the Lambda's 15 minutes.
    client = boto3.client(
        "lambda",
        config=Config(
            read_timeout=900, connect_timeout=10, retries={"max_attempts": 0}
        ),
    )
    payload = json.dumps(event).encode()

    def one(index: int) -> dict[str, Any]:
        response = client.invoke(FunctionName=function_name, Payload=payload)
        body = json.loads(response["Payload"].read())
        body["invocation"] = index
        if "FunctionError" in response:
            body["function_error"] = response["FunctionError"]
        return typing.cast(dict[str, Any], body)

    with ThreadPoolExecutor(max_workers=parallel) as pool:
        return list(pool.map(one, range(parallel)))


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(
        description="Elasticsearch write-throughput harness"
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    run_parser = subparsers.add_parser("run", help="Run an event in this process")
    run_parser.add_argument("--event-file", required=True)

    invoke_parser = subparsers.add_parser(
        "invoke", help="Fan an event out over Lambda invocations"
    )
    invoke_parser.add_argument("--function-name", required=True)
    invoke_parser.add_argument("--event-file", required=True)
    invoke_parser.add_argument("--parallel", type=int, default=1)

    args = parser.parse_args(argv)
    setup_logging(ExecutionContext(trace_id=get_trace_id(), pipeline_step="es_bench"))

    if args.command == "run":
        output: Any = run(BenchEvent.model_validate(_load_event(args.event_file)))
    else:
        output = invoke_many(
            args.function_name, _load_event(args.event_file), args.parallel
        )
    json.dump(output, sys.stdout, indent=2, default=str)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
