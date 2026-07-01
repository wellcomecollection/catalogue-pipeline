"""Factories for the FOLIO item-enrichment runtime.

Wires the config in ``folio.config`` to the items store and the
mod-inventory-storage client used by the enrichment step. The inventory URL and OKAPI
credentials come from env vars when set (handy for local runs) and otherwise from SSM.

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
from adapters.extractors.oai_pmh.folio.enrichment.okapi_auth import OkapiAuth
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
def _inventory_username() -> str:
    return os.getenv("FOLIO_INVENTORY_USERNAME") or get_ssm_parameter(
        config.SSM_INVENTORY_USERNAME
    )


@cache
def _inventory_password() -> str:
    return os.getenv("FOLIO_INVENTORY_PASSWORD") or get_ssm_parameter(
        config.SSM_INVENTORY_PASSWORD
    )


@cache
def _inventory_tenant() -> str:
    return os.getenv("FOLIO_INVENTORY_TENANT") or get_ssm_parameter(
        config.SSM_INVENTORY_TENANT
    )


def build_inventory_http_client() -> httpx.Client:
    """Build an authenticated client for mod-inventory-storage.

    ``enrichedInstances`` is an OKAPI/mod-inventory-storage endpoint. OKAPI tokens are
    short-lived, so the client logs in itself (POST ``/authn/login`` with the
    username/password from ``FOLIO_INVENTORY_*``/SSM) and refreshes on 401, sending
    ``x-okapi-token`` plus ``x-okapi-tenant``. The URL, credentials and tenant all come
    from ``FOLIO_INVENTORY_*`` env vars when set, otherwise SSM.
    """
    auth = OkapiAuth(
        base_url=_inventory_url(),
        tenant=_inventory_tenant(),
        username=_inventory_username(),
        password=_inventory_password(),
    )
    return httpx.Client(
        auth=auth,
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
