"""Factories for the FOLIO item-enrichment runtime (RFC 088 / Option C).

Wires the SSM-backed config in ``folio.config`` to the items store and the
mod-inventory-storage client used by the enrichment step.
"""

from __future__ import annotations

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
    return get_ssm_parameter(config.SSM_INVENTORY_URL)


@cache
def _inventory_token() -> str:
    return get_ssm_parameter(config.SSM_INVENTORY_TOKEN)


def build_inventory_http_client() -> httpx.Client:
    """Build an authenticated client for mod-inventory-storage.

    OKAPI expects a tenant header alongside the auth token; the tenant is supplied
    via ``FOLIO_INVENTORY_TENANT`` and omitted when unset (e.g. an edge gateway that
    injects it server-side).
    """
    headers = {"Authorization": _inventory_token()}
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
