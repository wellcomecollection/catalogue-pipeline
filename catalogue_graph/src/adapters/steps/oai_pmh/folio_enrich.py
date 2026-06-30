"""Enrichment step for the FOLIO adapter (RFC 088 / Option C).

Runs between the loader and the publish event. Given the bib changeset(s) produced
by the loader, it reads the changed instance ids, fetches their items/holdings from
mod-inventory-storage, and upserts them into the ``folio_items_table`` store. It
passes both the bib changeset and the new items changeset forward so the publish
event can carry them.

Running here (rather than at transform time) preserves transformer purity: a full
reindex never calls FOLIO, it just joins whatever is already in the items store.
"""

from __future__ import annotations

import argparse
import json
from typing import Any, cast

import structlog
from pydantic import BaseModel, Field

from adapters.extractors.oai_pmh.folio.enrichment.enricher import FolioItemEnricher
from adapters.extractors.oai_pmh.folio.enrichment.runtime import build_enricher
from adapters.extractors.oai_pmh.folio.runtime import FOLIO_CONFIG
from adapters.utils.adapter_store import AdapterStore
from utils.logger import ExecutionContext, get_trace_id, setup_logging

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


def collect_instance_ids(
    bib_store: AdapterStore, changeset_ids: list[str]
) -> list[str]:
    """Return the de-duplicated instance ids that changed across the bib changesets."""
    instance_ids: list[str] = []
    for changeset_id in changeset_ids:
        table = bib_store.get_records_by_changeset(changeset_id)
        instance_ids.extend(cast("list[str]", table.column("id").to_pylist()))
    return list(dict.fromkeys(instance_ids))


def run_enrichment(
    event: EnrichmentEvent,
    bib_store: AdapterStore,
    enricher: FolioItemEnricher,
) -> EnrichmentResponse:
    """Core enrichment logic: changed bib instances -> upserted item rows."""
    instance_ids = collect_instance_ids(bib_store, event.changeset_ids)
    logger.info(
        "Collected changed instance ids for enrichment",
        job_id=event.job_id,
        count=len(instance_ids),
    )

    update = enricher.enrich(instance_ids)
    items_changeset_ids = [update.changeset_id] if update else []

    return EnrichmentResponse(
        job_id=event.job_id,
        changeset_ids=event.changeset_ids,
        items_changeset_ids=items_changeset_ids,
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
    return run_enrichment(event, bib_store, enricher)


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


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the FOLIO enrichment step")
    parser.add_argument(
        "--event", type=str, required=True, help="Path to an enrichment event JSON file"
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
    main()
