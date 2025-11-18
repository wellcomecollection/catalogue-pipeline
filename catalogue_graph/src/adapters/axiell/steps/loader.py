"""Loader step for the Axiell adapter.

Harvests OAI-PMH windows requested by the trigger, persists raw records into
Iceberg, and emits a changeset identifier for the transformer step.
"""

import argparse
import json
from datetime import datetime
from typing import Any

import pyarrow as pa
from attr import dataclass
from lxml import etree
from oai_pmh_client.client import OAIClient
from oai_pmh_client.models import Record
from pydantic import BaseModel

from adapters.axiell import config
from adapters.axiell.clients import build_oai_client
from adapters.axiell.models import (
    AxiellAdapterLoaderEvent,
    LoaderResponse,
    WindowLoadResult,
)
from adapters.axiell.table_config import get_iceberg_table
from adapters.axiell.window_status import build_window_store
from adapters.utils.iceberg import IcebergTableClient
from adapters.utils.schemata import ARROW_SCHEMA
from adapters.utils.window_harvester import WindowHarvestManager
from adapters.utils.window_store import IcebergWindowStore

AXIELL_NAMESPACE = "axiell"


class LoaderConfig(BaseModel):
    use_rest_api_table: bool = True


def _serialize_metadata(record: Record) -> str | None:
    metadata = getattr(record, "metadata", None)
    if metadata is None:
        return None
    return etree.tostring(metadata, encoding="unicode", pretty_print=False)


class RecordAccumulator:
    def __init__(self, namespace: str) -> None:
        self.namespace = namespace
        self.rows: list[dict[str, str | None]] = []

    def __call__(
        self,
        identifier: str,
        record: Record,
        _window_start: datetime,
        _window_end: datetime,
        _index: int,
    ) -> bool:
        self.rows.append(
            {
                "namespace": self.namespace,
                "id": identifier,
                "content": _serialize_metadata(record),
            }
        )
        return True

    @property
    def count(self) -> int:
        return len(self.rows)

    def to_table(self) -> pa.Table | None:
        if not self.rows:
            return None
        return pa.Table.from_pylist(self.rows, schema=ARROW_SCHEMA)


@dataclass
class LoaderRuntime:
    store: IcebergWindowStore
    table_client: IcebergTableClient
    oai_client: OAIClient


def build_runtime(config_obj: LoaderConfig | None = None) -> LoaderRuntime:
    cfg = config_obj or LoaderConfig()
    store = build_window_store(use_rest_api_table=cfg.use_rest_api_table)
    table = get_iceberg_table(use_rest_api_table=cfg.use_rest_api_table)
    table_client = IcebergTableClient(table, default_namespace=AXIELL_NAMESPACE)
    oai_client = build_oai_client()

    return LoaderRuntime(store=store, table_client=table_client, oai_client=oai_client)


def _build_harvester(
    request: AxiellAdapterLoaderEvent,
    runtime: LoaderRuntime,
    accumulator: RecordAccumulator,
) -> WindowHarvestManager:
    return WindowHarvestManager(
        client=runtime.oai_client,
        store=runtime.store,
        metadata_prefix=request.metadata_prefix,
        set_spec=request.set_spec,
        window_minutes=config.WINDOW_MINUTES,
        max_parallel_requests=config.WINDOW_MAX_PARALLEL_REQUESTS,
        record_callback=accumulator,
        default_tags={"job_id": request.job_id},
    )


def execute_loader(
    request: AxiellAdapterLoaderEvent,
    runtime: LoaderRuntime | None = None,
) -> LoaderResponse:
    runtime = runtime or build_runtime()
    accumulator = RecordAccumulator(namespace=AXIELL_NAMESPACE)
    harvester = _build_harvester(request, runtime, accumulator)

    summaries = harvester.harvest_recent(
        start_time=request.window_start,
        end_time=request.window_end,
        max_windows=request.max_windows,
        reprocess_successful_windows=True,
    )

    if not summaries:
        raise RuntimeError(
            "No pending windows to harvest for "
            f"{request.window_start.isoformat()} -> {request.window_end.isoformat()}"
        )

    arrow_table = accumulator.to_table()
    changeset_id: str | None = None
    if arrow_table is not None and arrow_table.num_rows > 0:
        changeset_id = runtime.table_client.update(arrow_table)

    typed_summaries = [
        WindowLoadResult.model_validate(summary) for summary in summaries
    ]
    record_count = accumulator.count

    return LoaderResponse(
        summaries=typed_summaries,
        changeset_id=changeset_id,
        record_count=record_count,
        job_id=request.job_id,
    )


def handler(
    event: AxiellAdapterLoaderEvent, *, runtime: LoaderRuntime | None = None
) -> LoaderResponse:
    return execute_loader(event, runtime=runtime)


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    request = AxiellAdapterLoaderEvent.model_validate(event)
    response = handler(request)
    return response.model_dump(mode="json")


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the Axiell loader step locally")
    """ Example event: 
    {
        "job_id": "some-unique-job-id",
        "window_key": "2025-11-17T16:46:41.071426+00:00_2025-11-17T16:50:05.531331+00:00",
        "window_start": "2025-11-17T16:46:41.071426Z",
        "window_end": "2025-11-17T16:50:05.531331Z",
        "metadata_prefix": "oai_raw",
        "set_spec": "collect",
        "max_windows": null
    }
    """
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

    runtime = build_runtime(LoaderConfig(use_rest_api_table=args.use_rest_api_table))
    response = handler(event, runtime=runtime)

    print(json.dumps(response.model_dump(mode="json"), indent=2))


if __name__ == "__main__":
    main()
