"""Loader step for the Axiell adapter.

Harvests OAI-PMH windows requested by the trigger, persists raw records into
Iceberg, and emits a changeset identifier for the transformer step.
"""

import argparse
import json
import logging
from datetime import datetime
from typing import Any

from oai_pmh_client.client import OAIClient
from pydantic import BaseModel, ConfigDict

from adapters.axiell import config, helpers
from adapters.axiell.clients import build_oai_client
from adapters.axiell.models.step_events import (
    AxiellAdapterLoaderEvent,
    LoaderResponse,
)
from adapters.axiell.record_writer import WindowRecordWriter
from adapters.axiell.reporting import AxiellLoaderReport
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_generator import WindowGenerator
from adapters.utils.window_harvester import (
    WindowHarvestManager,
)
from adapters.utils.window_store import WindowStore

AXIELL_NAMESPACE = "axiell"

logging.basicConfig(level=logging.INFO)


class AxiellAdapterLoaderConfig(BaseModel):
    use_rest_api_table: bool = True
    window_minutes: int | None = None
    allow_partial_final_window: bool = False


def _format_window_range(start: datetime, end: datetime) -> str:
    return f"{start.isoformat()}-{end.isoformat()}"


class LoaderRuntime(BaseModel):
    store: WindowStore
    table_client: AdapterStore
    oai_client: OAIClient
    window_generator: WindowGenerator

    model_config = ConfigDict(arbitrary_types_allowed=True)


def build_runtime(
    config_obj: AxiellAdapterLoaderConfig | None = None,
) -> LoaderRuntime:
    cfg = config_obj or AxiellAdapterLoaderConfig()
    store = helpers.build_window_store(use_rest_api_table=cfg.use_rest_api_table)
    table = helpers.build_adapter_table(use_rest_api_table=cfg.use_rest_api_table)
    table_client = AdapterStore(table, default_namespace=AXIELL_NAMESPACE)
    oai_client = build_oai_client()

    window_generator = WindowGenerator(
        window_minutes=cfg.window_minutes or config.WINDOW_MINUTES,
        allow_partial_final_window=cfg.allow_partial_final_window,
    )

    return LoaderRuntime(
        store=store,
        table_client=table_client,
        oai_client=oai_client,
        window_generator=window_generator,
    )


def build_harvester(
    request: AxiellAdapterLoaderEvent,
    runtime: LoaderRuntime,
) -> WindowHarvestManager:
    window_start = request.window.start_time
    window_end = request.window.end_time
    callback = WindowRecordWriter(
        namespace=AXIELL_NAMESPACE,
        table_client=runtime.table_client,
        job_id=request.job_id,
        window_range=_format_window_range(window_start, window_end),
    )
    return WindowHarvestManager(
        store=runtime.store,
        window_generator=runtime.window_generator,
        client=runtime.oai_client,
        metadata_prefix=request.metadata_prefix,
        set_spec=request.set_spec,
        max_parallel_requests=config.WINDOW_MAX_PARALLEL_REQUESTS,
        record_callback=callback,
        default_tags={"job_id": request.job_id},
    )


def execute_loader(
    request: AxiellAdapterLoaderEvent,
    runtime: LoaderRuntime | None = None,
) -> LoaderResponse:
    window_start = request.window.start_time
    window_end = request.window.end_time
    runtime = runtime or build_runtime()
    harvester = build_harvester(request, runtime)

    summaries = harvester.harvest_range(
        start_time=window_start,
        end_time=window_end,
        max_windows=request.max_windows,
        reprocess_successful_windows=False,
    )

    if not summaries:
        raise RuntimeError(
            "No pending windows to harvest for "
            f"{window_start.isoformat()} -> {window_end.isoformat()}"
        )

    changed_record_count = 0
    changeset_ids: set[str] = set()

    for summary in summaries:
        if not summary.tags:
            continue

        if "changeset_id" in summary.tags:
            changeset_ids.add(summary.tags["changeset_id"])

        if "record_ids_changed" in summary.tags:
            changed_ids = json.loads(summary.tags["record_ids_changed"])
            changed_record_count += len(changed_ids)

    return LoaderResponse(
        summaries=summaries,
        changeset_ids=list(changeset_ids),
        changed_record_count=changed_record_count,
        job_id=request.job_id,
    )


def handler(
    event: AxiellAdapterLoaderEvent, *, runtime: LoaderRuntime | None = None
) -> LoaderResponse:
    response = execute_loader(event, runtime=runtime)

    report = AxiellLoaderReport.from_loader(event, response)
    report.publish()

    return response


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    request = AxiellAdapterLoaderEvent.model_validate(event)
    runtime = build_runtime()
    response = handler(request, runtime=runtime)
    return response.model_dump(mode="json")


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the Axiell loader step locally")
    """  
    Runs the Axiell loader step locally.  

    Parses command-line arguments to load an event JSON file and optional configuration,  
    then executes the loader step and prints the response.  
    """
    # Example event payload:
    # {
    #     "job_id": "some-unique-job-id",
    #     "window_start": "2025-11-17T16:46:41.071426Z",
    #     "window_end": "2025-11-17T16:50:05.531331Z",
    #     "metadata_prefix": "oai_raw",
    #     "set_spec": "collect",
    #     "max_windows": null
    # }
    parser.add_argument(
        "--event",
        type=str,
        required=True,
        help="Path to a WindowRequest JSON payload",
    )
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Use the S3 Tables catalog instead of local storage",
    )
    parser.add_argument(
        "--allow-partial-final-window",
        action="store_true",
        default=True,
        help="Allow partial final window (default: True for CLI)",
    )
    args = parser.parse_args()

    with open(args.event, encoding="utf-8") as f:
        event_data = json.load(f)
        # Set allow_partial_final_window from CLI arg if not in event
        if "allow_partial_final_window" not in event_data:
            event_data["allow_partial_final_window"] = args.allow_partial_final_window
        event = AxiellAdapterLoaderEvent.model_validate(event_data)

    runtime = build_runtime(
        AxiellAdapterLoaderConfig(
            use_rest_api_table=args.use_rest_api_table,
            window_minutes=event.window_minutes,
            allow_partial_final_window=(
                event.allow_partial_final_window
                if event.allow_partial_final_window is not None
                else args.allow_partial_final_window
            ),
        )
    )
    response = handler(event, runtime=runtime)

    print(json.dumps(response.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    main()
