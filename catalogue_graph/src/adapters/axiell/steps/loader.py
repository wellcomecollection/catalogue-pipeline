"""Loader step for the Axiell adapter.

Harvests OAI-PMH windows requested by the trigger, persists raw records into
Iceberg, and emits a changeset identifier for the transformer step.
"""

import argparse
import json
import logging
from datetime import datetime
from typing import Any

import pyarrow as pa
from lxml import etree
from oai_pmh_client.client import OAIClient
from oai_pmh_client.models import Record
from pydantic import BaseModel, Field, ConfigDict

from adapters.axiell import config
from adapters.axiell.clients import build_oai_client
from adapters.axiell.models.step_events import (
    AxiellAdapterLoaderEvent,
)
from adapters.axiell.table_config import get_iceberg_table
from adapters.axiell.window_status import build_window_store
from adapters.utils.iceberg import IcebergTableClient
from adapters.utils.schemata import ARROW_SCHEMA
from adapters.utils.window_harvester import (
    WindowCallbackResult,
    WindowHarvestManager,
    WindowProcessor,
)
from adapters.utils.window_store import IcebergWindowStore

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
    changeset_id: str | None = None
    last_error: str | None = None


class LoaderResponse(BaseModel):
    summaries: list[WindowLoadResult]
    changeset_ids: list[str] = Field(default_factory=list)
    record_count: int
    job_id: str


def _serialize_metadata(record: Record) -> str | None:
    metadata = getattr(record, "metadata", None)
    if metadata is None:
        return None
    return etree.tostring(metadata, encoding="unicode", pretty_print=False)


class WindowRecordWriter:
    def __init__(
        self,
        *,
        namespace: str,
        table_client: IcebergTableClient,
        job_id: str,
    ) -> None:
        self.namespace = namespace
        self.table_client = table_client
        self.job_id = job_id
        self._rows: list[dict[str, str | None]] = []

    def start_window(self, window_key: str) -> None:
        self._rows = []

    def process_record(self, identifier: str, record: Record) -> None:
        self._rows.append(
            {
                "namespace": self.namespace,
                "id": identifier,
                "content": _serialize_metadata(record),
            }
        )

    def complete_window(
        self,
        window_key: str,
        record_ids: list[str],
    ) -> WindowCallbackResult:
        tags: dict[str, str] = {"job_id": self.job_id, "window_key": window_key}
        changeset_id: str | None = None

        if self._rows:
            table = pa.Table.from_pylist(self._rows, schema=ARROW_SCHEMA)
            changeset_id = self.table_client.incremental_update(table)

        if changeset_id:
            tags["changeset_id"] = changeset_id

        return {"record_ids": record_ids, "tags": tags}


class WindowRecordWriterFactory:
    def __init__(self, *, table_client: IcebergTableClient, job_id: str) -> None:
        self.table_client = table_client
        self.job_id = job_id

    def __call__(
        self, window_key: str, window_start: datetime, window_end: datetime
    ) -> WindowProcessor:
        writer = WindowRecordWriter(
            namespace=AXIELL_NAMESPACE,
            table_client=self.table_client,
            job_id=self.job_id,
        )
        return writer


class LoaderRuntime(BaseModel):
    store: IcebergWindowStore
    table_client: IcebergTableClient
    oai_client: OAIClient

    model_config = ConfigDict(arbitrary_types_allowed=True)


def build_runtime(config_obj: AxiellAdapterLoaderConfig | None = None) -> LoaderRuntime:
    cfg = config_obj or AxiellAdapterLoaderConfig()
    store = build_window_store(use_rest_api_table=cfg.use_rest_api_table)
    table = get_iceberg_table(use_rest_api_table=cfg.use_rest_api_table)
    table_client = IcebergTableClient(table, default_namespace=AXIELL_NAMESPACE)
    oai_client = build_oai_client()

    return LoaderRuntime(store=store, table_client=table_client, oai_client=oai_client)


def _build_harvester(
    request: AxiellAdapterLoaderEvent,
    runtime: LoaderRuntime,
) -> WindowHarvestManager:
    callback_factory = WindowRecordWriterFactory(
        table_client=runtime.table_client,
        job_id=request.job_id,
    )
    return WindowHarvestManager(
        client=runtime.oai_client,
        store=runtime.store,
        metadata_prefix=request.metadata_prefix,
        set_spec=request.set_spec,
        window_minutes=config.WINDOW_MINUTES,
        max_parallel_requests=config.WINDOW_MAX_PARALLEL_REQUESTS,
        record_callback_factory=callback_factory,
        default_tags={"job_id": request.job_id},
    )


def execute_loader(
    request: AxiellAdapterLoaderEvent,
    runtime: LoaderRuntime | None = None,
) -> LoaderResponse:
    runtime = runtime or build_runtime()
    harvester = _build_harvester(request, runtime)

    summaries = harvester.harvest_recent(
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
    record_count = sum(len(summary.record_ids) for summary in typed_summaries)
    changeset_ids: set[str] = set()

    for summary in typed_summaries:
        if summary.changeset_id is None:
            continue
        changeset_ids.add(summary.changeset_id)

    return LoaderResponse(
        summaries=typed_summaries,
        changeset_ids=list(changeset_ids),
        record_count=record_count,
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
    #     "window_key": "2025-11-17T16:46:41.071426+00:00_2025-11-17T16:50:05.531331+00:00",
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
