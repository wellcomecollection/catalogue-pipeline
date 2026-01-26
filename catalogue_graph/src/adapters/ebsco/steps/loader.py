"""Loader step for the EBSCO adapter.

Reads a MARC XML file (local path or S3 URI), extracts record IDs + raw XML,
and upserts them into the Iceberg table (Glue or local). Returns a changeset
identifier used by the transformer; skips work if the file was already loaded.
"""

import argparse
from datetime import datetime
from typing import Any

import pyarrow as pa
import structlog
from pydantic import BaseModel, ConfigDict

from adapters.ebsco import helpers
from adapters.ebsco.marcxml_loader import MarcXmlFileLoader
from adapters.ebsco.models.step_events import (
    EbscoAdapterLoaderEvent,
    LoaderResponse,
)
from adapters.ebsco.reporting import EbscoLoaderReport
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

EBSCO_NAMESPACE = "ebsco"


class EbscoAdapterLoaderConfig(BaseModel):
    use_rest_api_table: bool = True
    namespace: str = EBSCO_NAMESPACE
    table_schema: pa.Schema = ARROW_SCHEMA

    model_config = ConfigDict(arbitrary_types_allowed=True)


class LoaderRuntime(BaseModel):
    adapter_store: AdapterStore
    marcxml_loader: MarcXmlFileLoader

    model_config = ConfigDict(arbitrary_types_allowed=True)


def build_runtime(
    config_obj: EbscoAdapterLoaderConfig | None = None,
) -> LoaderRuntime:
    cfg = config_obj or EbscoAdapterLoaderConfig()
    table = helpers.build_adapter_table(use_rest_api_table=cfg.use_rest_api_table)
    adapter_store = AdapterStore(table, default_namespace=cfg.namespace)
    marcxml_loader = MarcXmlFileLoader(
        schema=cfg.table_schema,
        namespace=cfg.namespace,
    )

    return LoaderRuntime(
        adapter_store=adapter_store,
        marcxml_loader=marcxml_loader,
    )


def execute_loader(
    request: EbscoAdapterLoaderEvent,
    runtime: LoaderRuntime | None = None,
) -> LoaderResponse:
    runtime = runtime or build_runtime()

    pa_table = runtime.marcxml_loader.load_file(request.file_location)
    changeset = runtime.adapter_store.snapshot_sync(pa_table)
    changed_record_count = len(changeset.updated_record_ids) if changeset else 0

    return LoaderResponse(
        changeset_ids=[changeset.changeset_id] if changeset else [],
        changed_record_count=changed_record_count,
        job_id=request.job_id,
    )


def handler(
    event: EbscoAdapterLoaderEvent,
    execution_context: ExecutionContext,
    runtime: LoaderRuntime | None = None,
) -> LoaderResponse:
    setup_logging(execution_context)
    loader_response = execute_loader(event, runtime=runtime)

    report = EbscoLoaderReport.from_loader(event, loader_response)
    report.publish()

    return loader_response


def lambda_handler(event: EbscoAdapterLoaderEvent, context: Any) -> dict[str, Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="ebsco_adapter_loader",
    )
    request = EbscoAdapterLoaderEvent.model_validate(event)
    runtime = build_runtime()
    response = handler(request, execution_context, runtime=runtime)
    return response.model_dump()


def main() -> None:
    parser = argparse.ArgumentParser(description="Process XML file with EBSCO adapter")
    parser.add_argument(
        "xmlfile",
        type=str,
        help="Path to the XML file to process (supports local paths and S3 URLs)",
    )
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Use S3 Tables Iceberg REST API table instead of local table",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        required=False,
        help="Optional job id (defaults to current time if omitted)",
    )

    args = parser.parse_args()

    job_id = args.job_id or datetime.now().strftime("%Y%m%dT%H%M")

    event = EbscoAdapterLoaderEvent(file_location=args.xmlfile, job_id=job_id)
    config_obj = EbscoAdapterLoaderConfig(use_rest_api_table=args.use_rest_api_table)
    runtime = build_runtime(config_obj)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="ebsco_adapter_loader",
    )

    response = handler(
        event=event, execution_context=execution_context, runtime=runtime
    )
    logger.info("Loader response", response=response.model_dump(mode="json"))


if __name__ == "__main__":
    main()
