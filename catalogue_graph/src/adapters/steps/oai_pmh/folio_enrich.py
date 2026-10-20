"""Enrichment step for the FOLIO adapter.

Runs between the loader and the publish event. Given the bib changeset(s) produced
by the loader, it reads the changed instance ids, fetches their items/holdings from
mod-inventory-storage, and upserts them into the ``folio_inventory_table``
store. It
passes both the bib changeset and the new items changeset forward so the publish
event can carry them.

Running here (rather than at transform time) preserves transformer purity: a full
reindex never calls FOLIO, it just joins whatever is already in the inventory store.

See https://github.com/wellcomecollection/catalogue-pipeline/pull/3438 for the design.
"""

from __future__ import annotations

import argparse
import json
from typing import Any, Protocol, cast

import structlog
from pydantic import BaseModel, Field

from adapters.extractors.oai_pmh.folio.enrichment.enricher import (
    FolioInventoryEnricher,
    SystemicRejectionError,
)
from adapters.extractors.oai_pmh.folio.enrichment.models import RejectedInventoryRecord
from adapters.extractors.oai_pmh.folio.enrichment.reporting import FolioEnrichmentReport
from adapters.extractors.oai_pmh.folio.enrichment.runtime import build_enricher
from adapters.extractors.oai_pmh.folio.runtime import FOLIO_CONFIG
from adapters.utils.adapter_store import AdapterStore
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import ecs_handler

logger = structlog.get_logger(__name__)


class EnrichmentEvent(BaseModel):
    """Input to the enrichment step (a subset of the loader response)."""

    job_id: str
    changeset_ids: list[str] = Field(default_factory=list)


class EnrichmentResponse(BaseModel):
    """Output of the enrichment step, carrying both changesets forward."""

    job_id: str
    changeset_ids: list[str] = Field(default_factory=list)
    items_changeset_ids: list[str] = Field(default_factory=list)
    requested_count: int = 0
    rejected_count: int = 0


def collect_instance_ids(
    bib_store: AdapterStore, changeset_ids: list[str]
) -> list[str]:
    """Return the de-duplicated instance ids that changed across the bib changesets."""
    # get_records_by_changesets bounds the content read by the changesets' minimum
    # last_modified, so file stats can prune (the plain changeset scan cannot).
    table = bib_store.get_records_by_changesets(changeset_ids)
    instance_ids = cast("list[str]", table.column("id").to_pylist())
    return list(dict.fromkeys(instance_ids))


class RejectionReporter(Protocol):
    def __call__(
        self, requested: int, rejected: list[RejectedInventoryRecord], failed: bool
    ) -> None: ...


def _no_report(
    requested: int, rejected: list[RejectedInventoryRecord], failed: bool
) -> None:
    pass


def run_enrichment(
    event: EnrichmentEvent,
    bib_store: AdapterStore,
    enricher: FolioInventoryEnricher,
    report: RejectionReporter = _no_report,
) -> EnrichmentResponse:
    """Core enrichment logic: changed bib instances -> upserted item rows.

    ``report`` is called with the rejected records before the step fails on
    systemic rejection too, so the list survives the run that needs it most.
    """
    instance_ids = collect_instance_ids(bib_store, event.changeset_ids)
    logger.info(
        "Collected changed instance ids for enrichment",
        job_id=event.job_id,
        count=len(instance_ids),
    )

    try:
        update, rejected = enricher.enrich(instance_ids)
    except SystemicRejectionError as e:
        report(len(instance_ids), e.rejected, True)
        raise
    report(len(instance_ids), rejected, False)
    items_changeset_ids = [update.changeset_id] if update else []

    return EnrichmentResponse(
        job_id=event.job_id,
        changeset_ids=event.changeset_ids,
        items_changeset_ids=items_changeset_ids,
        requested_count=len(instance_ids),
        rejected_count=len(rejected),
    )


def handler(
    event: EnrichmentEvent,
    *,
    use_rest_api_table: bool = True,
    execution_context: ExecutionContext | None = None,
) -> EnrichmentResponse:
    setup_logging(execution_context)
    bib_store = FOLIO_CONFIG.build_adapter_store(use_rest_api_table=use_rest_api_table)
    enricher = build_enricher(use_rest_api_table=use_rest_api_table)
    config = FOLIO_CONFIG.config

    def report(
        requested: int, rejected: list[RejectedInventoryRecord], failed: bool
    ) -> None:
        FolioEnrichmentReport.build(
            adapter_type=config.adapter_name,
            job_id=event.job_id,
            requested_count=requested,
            rejected=rejected,
            failed=failed,
            # Local runs keep the list in the logs only.
            report_s3_bucket=config.report_s3_bucket if use_rest_api_table else None,
            report_s3_prefix=config.report_s3_prefix,
            emit_metrics=use_rest_api_table,
        ).publish()

    return run_enrichment(event, bib_store, enricher, report)


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="folio_adapter_enrichment",
    )
    return handler(
        EnrichmentEvent.model_validate(event),
        use_rest_api_table=True,
        execution_context=execution_context,
    ).model_dump(mode="json")


def ecs_task_handler(
    event: EnrichmentEvent,
    execution_context: ExecutionContext,
) -> EnrichmentResponse:
    """ECS task handler invoked by the ecs_handler utility (waitForTaskToken)."""
    return handler(event, use_rest_api_table=True, execution_context=execution_context)


def event_validator(raw_input: str) -> EnrichmentEvent:
    return EnrichmentEvent.model_validate(json.loads(raw_input))


def local_handler(parser: argparse.ArgumentParser) -> None:
    """Run the enrichment step from the command line against a local event file.

    Defaults to local Iceberg tables; pass --use-rest-api-table for S3 Tables.
    """
    parser.add_argument(
        "--event",
        type=str,
        required=True,
        help="Path to an enrichment event JSON file (e.g. the loader response)",
    )
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Use the S3 Tables catalog instead of local storage",
    )
    args = parser.parse_args()

    with open(args.event, encoding="utf-8") as f:
        event = EnrichmentEvent.model_validate(json.load(f))

    response = handler(event, use_rest_api_table=args.use_rest_api_table)
    print(json.dumps(response.model_dump(mode="json")))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run the FOLIO enrichment step")
    parser.add_argument(
        "--use-cli",
        action="store_true",
        help="Invoke the local CLI handler instead of the ECS handler.",
    )
    cli_args, _ = parser.parse_known_args()

    if cli_args.use_cli:
        local_handler(parser)
    else:
        ecs_handler(
            arg_parser=parser,
            handler=ecs_task_handler,
            event_validator=event_validator,
            pipeline_step="folio_adapter_enrichment",
        )
