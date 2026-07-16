"""Typed callable protocols used by the Axiell -> FOLIO sync path."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any, Protocol


class FolioInventoryOps(Protocol):
    """Inventory operations consumed by the Axiell -> FOLIO sync path."""

    def get(
        self, path: str, params: Mapping[str, Any] | None = None
    ) -> dict[str, Any]: ...

    def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]: ...

    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]: ...

    def delete(self, path: str) -> dict[str, Any]: ...
