"""Client for the FOLIO mod-inventory-storage ``oai-pmh-view`` API.

This is the source of the stable item UUIDs that the OAI-PMH feed does not carry.
We only use the read-only ``enrichedInstances`` endpoint: given a batch of instance
ids it returns each instance's items and holdings, keyed by instance id.

The endpoint is distinct from the OAI-PMH feed (different base URL and credentials),
so it has its own SSM-backed configuration in ``folio.config``.
"""

from __future__ import annotations

import json
from collections.abc import Iterable, Iterator
from typing import cast

import httpx
import structlog

from adapters.extractors.oai_pmh.folio.enrichment.models import FolioEnrichedInstance

logger = structlog.get_logger(__name__)

ENRICHED_INSTANCES_PATH = "oai-pmh-view/enrichedInstances"


def _chunk(ids: list[str], size: int) -> Iterator[list[str]]:
    for start in range(0, len(ids), size):
        yield ids[start : start + size]


class FolioInventoryClient:
    """Read-only client for mod-inventory-storage enrichment lookups.

    Args:
        base_url: Base URL of the inventory-storage API (OKAPI gateway or edge).
        client: An ``httpx.Client`` carrying auth (tenant/token) headers. Injected
            so it can be mocked in tests.
        batch_size: Maximum number of instance ids per request.
        skip_suppressed: Whether to ask the API to omit discovery-suppressed records.
    """

    def __init__(
        self,
        *,
        base_url: str,
        client: httpx.Client,
        batch_size: int = 50,
        skip_suppressed: bool = False,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._client = client
        self._batch_size = batch_size
        self._skip_suppressed = skip_suppressed

    def enriched_instances(
        self, instance_ids: Iterable[str]
    ) -> list[FolioEnrichedInstance]:
        """Fetch items and holdings for the given instance ids, batching requests.

        Instance ids missing from the response (e.g. deleted instances) are simply
        absent from the result; callers should treat that as "no items".
        """
        unique_ids = list(dict.fromkeys(i for i in instance_ids if i))
        results: list[FolioEnrichedInstance] = []

        for batch in _chunk(unique_ids, self._batch_size):
            results.extend(self._fetch_batch(batch))

        logger.info(
            "Fetched enriched instances",
            requested=len(unique_ids),
            returned=len(results),
        )
        return results

    def _fetch_batch(self, instance_ids: list[str]) -> list[FolioEnrichedInstance]:
        response = self._client.post(
            f"{self._base_url}/{ENRICHED_INSTANCES_PATH}",
            json={
                "instanceIds": instance_ids,
                "skipSuppressedFromDiscoveryRecords": self._skip_suppressed,
            },
        )
        response.raise_for_status()
        return [
            FolioEnrichedInstance.from_api(record)
            for record in _parse_records(response)
        ]


def _parse_records(response: httpx.Response) -> list[dict]:
    """Parse the response body into a list of record dicts.

    ``enrichedInstances`` may return a JSON array, a single JSON object wrapping the
    records, or newline-delimited JSON. Handle all three rather than assuming one.
    """
    text = response.text.strip()
    if not text:
        return []

    try:
        payload = response.json()
    except json.JSONDecodeError:
        # Newline-delimited JSON (one record per line).
        return [json.loads(line) for line in text.splitlines() if line.strip()]

    if isinstance(payload, list):
        return cast("list[dict]", payload)
    if isinstance(payload, dict):
        for key in ("enrichedInstances", "instances", "records"):
            if isinstance(payload.get(key), list):
                return cast("list[dict]", payload[key])
        return [payload]
    return []
