"""Factories for the FOLIO item-enrichment runtime.

Wires the config in ``folio.config`` to the items store and the
mod-inventory-storage client used by the enrichment step. The inventory URL/token
come from env vars when set (handy for local runs) and otherwise from SSM.

See https://github.com/wellcomecollection/catalogue-pipeline/pull/3438 for the design.
"""

from __future__ import annotations

import os
from functools import cache

import httpx

from adapters.extractors.oai_pmh.folio import config
from adapters.extractors.oai_pmh.folio.enrichment.enricher import FolioItemEnricher
from adapters.extractors.oai_pmh.folio.enrichment.inventory_client import (
    FolioInventoryClient,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg import get_local_table, get_rest_api_table
from utils.aws import get_ssm_parameter


def build_items_store(*, use_rest_api_table: bool = True) -> AdapterStore:
    """Build the FOLIO items store (the ``folio_items_table``)."""
    if use_rest_api_table:
        table = get_rest_api_table(
            config.ITEMS_REST_API_ICEBERG_CONFIG, create_if_not_exists=True
        )
    else:
        table = get_local_table(
            config.ITEMS_LOCAL_ICEBERG_CONFIG, create_if_not_exists=True
        )
    return AdapterStore(table, namespace=config.ITEMS_NAMESPACE)


@cache
def _inventory_url() -> str:
    # Prefer an explicit env var (local runs / overrides), else read from SSM.
    return os.getenv("FOLIO_INVENTORY_URL") or get_ssm_parameter(
        config.SSM_INVENTORY_URL
    )


@cache
def _inventory_token() -> str:
    return os.getenv("FOLIO_INVENTORY_TOKEN") or get_ssm_parameter(
        config.SSM_INVENTORY_TOKEN
    )


def build_inventory_http_client() -> httpx.Client:
    """Build an authenticated client for mod-inventory-storage.

    ``enrichedInstances`` is an OKAPI/mod-inventory-storage endpoint, so it expects
    ``x-okapi-token`` plus ``x-okapi-tenant`` (NOT the ``Authorization`` token the edge
    OAI feed uses). The token comes from ``FOLIO_INVENTORY_TOKEN``/SSM and the tenant
    from ``FOLIO_INVENTORY_TENANT`` (omitted when unset, e.g. a gateway that injects it).
    """
    headers = {"x-okapi-token": _inventory_token()}
    if config.INVENTORY_TENANT:
        headers["x-okapi-tenant"] = config.INVENTORY_TENANT
    return httpx.Client(
        headers=headers,
        timeout=httpx.Timeout(
            config.OAI_HTTP_TIMEOUT, read=config.OAI_MAX_READ_TIMEOUT
        ),
    )


def build_inventory_client(
    *, http_client: httpx.Client | None = None
) -> FolioInventoryClient:
    """Build the read-only mod-inventory-storage enrichment client."""
    return FolioInventoryClient(
        base_url=_inventory_url(),
        client=http_client or build_inventory_http_client(),
        batch_size=config.ENRICH_BATCH_SIZE,
        skip_suppressed=config.ENRICH_SKIP_SUPPRESSED,
    )


def build_enricher(*, use_rest_api_table: bool = True) -> FolioItemEnricher:
    """Build a fully wired enricher for the enrichment step."""
    return FolioItemEnricher(
        inventory_client=build_inventory_client(),
        items_store=build_items_store(use_rest_api_table=use_rest_api_table),
    )
