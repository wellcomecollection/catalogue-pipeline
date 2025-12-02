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
from pydantic import BaseModel, ConfigDict, Field

from adapters.axiell import config, helpers
from adapters.axiell.clients import build_oai_client
from adapters.axiell.models.step_events import (
    AxiellAdapterLoaderEvent,
)
from adapters.axiell.record_writer import WindowRecordWriter
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.window_harvester import (
    WindowHarvestManager,
)
from adapters.utils.window_store import WindowStore

AXIELL_NAMESPACE = "axiell"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    force=True,
)


class AxiellAdapterLoaderConfig(BaseModel):
    use_rest_api_table: bool = True


class WindowLoadResult(BaseModel):
    window_key: str
    window_start: datetime
    window_end: datetime
    state: str
    attempts: int
    record_ids: list[str]
    tags: dict[str, str] | None = None
    last_error: str | None = None


class LoaderResponse(BaseModel):
    summaries: list[WindowLoadResult]
    changeset_ids: list[str] = Field(default_factory=list)
    changed_record_count: int
    job_id: str


def _format_window_range(start: datetime, end: datetime) -> str:
    return f"{start.isoformat()}-{end.isoformat()}"


class LoaderRuntime(BaseModel):
    store: WindowStore
    table_client: AdapterStore
    oai_client: OAIClient

    model_config = ConfigDict(arbitrary_types_allowed=True)


def build_runtime(config_obj: AxiellAdapterLoaderConfig | None = None) -> LoaderRuntime:
    cfg = config_obj or AxiellAdapterLoaderConfig()
    store = helpers.build_window_store(use_rest_api_table=cfg.use_rest_api_table)
    table = helpers.build_adapter_table(cfg.use_rest_api_table)
    table_client = AdapterStore(table, default_namespace=AXIELL_NAMESPACE)
    oai_client = build_oai_client()

    return LoaderRuntime(store=store, table_client=table_client, oai_client=oai_client)


def build_harvester(
    request: AxiellAdapterLoaderEvent,
    runtime: LoaderRuntime,
) -> WindowHarvestManager:
    callback = WindowRecordWriter(
        namespace=AXIELL_NAMESPACE,
        table_client=runtime.table_client,
        job_id=request.job_id,
        window_range=_format_window_range(request.window_start, request.window_end),
    )
    return WindowHarvestManager(
        client=runtime.oai_client,
        store=runtime.store,
        metadata_prefix=request.metadata_prefix,
        set_spec=request.set_spec,
        window_minutes=request.window_minutes or config.WINDOW_MINUTES,
        max_parallel_requests=config.WINDOW_MAX_PARALLEL_REQUESTS,
        record_callback=callback,
        default_tags={"job_id": request.job_id},
    )


def execute_loader(
    request: AxiellAdapterLoaderEvent,
    runtime: LoaderRuntime | None = None,
) -> LoaderResponse:
    runtime = runtime or build_runtime()
    harvester = build_harvester(request, runtime)

    summaries = harvester.harvest_range(
        start_time=request.window_start,
        end_time=request.window_end,
        max_windows=request.max_windows,
        reprocess_successful_windows=False,
    )

    if not summaries:
        raise RuntimeError(
            "No pending windows to harvest for "
            f"{request.window_start.isoformat()} -> {request.window_end.isoformat()}"
        )

    typed_summaries = [
        WindowLoadResult.model_validate(summary) for summary in summaries
    ]
    changed_record_count = 0
    changeset_ids: set[str] = set()

    for summary in typed_summaries:
        if not summary.tags:
            continue

        if "changeset_id" in summary.tags:
            changeset_ids.add(summary.tags["changeset_id"])

        if "record_ids_changed" in summary.tags:
            changed_ids = json.loads(summary.tags["record_ids_changed"])
            changed_record_count += len(changed_ids)

    return LoaderResponse(
        summaries=typed_summaries,
        changeset_ids=list(changeset_ids),
        changed_record_count=changed_record_count,
        job_id=request.job_id,
    )


def handler(
    event: AxiellAdapterLoaderEvent, *, runtime: LoaderRuntime | None = None
) -> LoaderResponse:
    return execute_loader(event, runtime=runtime)


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
    args = parser.parse_args()

    with open(args.event, encoding="utf-8") as f:
        event = AxiellAdapterLoaderEvent.model_validate(json.load(f))

    runtime = build_runtime(
        AxiellAdapterLoaderConfig(use_rest_api_table=args.use_rest_api_table)
    )
    response = handler(event, runtime=runtime)

    print(json.dumps(response.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    main()
