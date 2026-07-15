"""Higher-level FOLIO Inventory client.

Wraps :class:`~clients.folio_client.FolioClient` with the four typed operations
used by the Axiell to Folio sync step (get, post, put, delete), applying the
appropriate error semantics for each HTTP verb.

Separating this from :class:`FolioClient` keeps the low-level HTTP layer generic
and gives the sync step a single object to pass around instead of four loose
callables.
"""

from __future__ import annotations

import urllib.parse
from collections.abc import Mapping
from typing import Any, cast

from .client import FolioClient


class FolioInventoryClient:
    """Inventory-scoped wrapper around :class:`FolioClient`.

    Each method maps one HTTP verb to the error semantics expected by the
    Inventory upsert path:

    - ``get``    — raises on any 4xx/5xx.
    - ``post``   — raises unless the response is 200 or 201.
    - ``put``    — raises on any 4xx/5xx (many PUTs return 204 with no body).
    - ``delete`` — raises on 4xx/5xx *except* 404 (already gone → no-op).

    All four methods return a ``dict``; an empty ``{}`` is returned when the
    OKAPI response has no body (e.g. 204).
    """

    def __init__(self, client: FolioClient) -> None:
        self._client = client

    def get(
        self, path: str, params: Mapping[str, Any] | None = None
    ) -> dict[str, Any]:
        if params:
            path = f"{path}?{urllib.parse.urlencode(params)}"
        status, data = self._client.request("GET", path)
        if status >= 400:
            raise RuntimeError(f"GET {path} failed: {status} {str(data)[:200]}")
        return cast("dict[str, Any]", data)

    def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        status, data = self._client.request("POST", path, dict(payload))
        if status not in (200, 201):
            raise RuntimeError(f"POST {path} failed: {status} {str(data)[:200]}")
        return cast("dict[str, Any]", data)

    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        status, data = self._client.request(
            "PUT", path, dict(payload), accept="text/plain"
        )
        if status >= 400:
            raise RuntimeError(f"PUT {path} failed: {status} {str(data)[:200]}")
        return cast("dict[str, Any]", data)

    def delete(self, path: str) -> dict[str, Any]:
        status, data = self._client.request("DELETE", path, accept="text/plain")
        if status >= 400 and status != 404:
            raise RuntimeError(f"DELETE {path} failed: {status} {str(data)[:200]}")
        return cast("dict[str, Any]", data)
