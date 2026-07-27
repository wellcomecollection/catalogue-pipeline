"""Tests for the reconciler suppression path (``suppress_by_guid``).

A superseded GUID from the reconciler is an authoritative delete: the three
FOLIO records keyed on ``AxC-{entity}-{guid}`` are soft-suppressed (both
suppress flags), child-first, reversibly.
"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from adapters.steps.axiell_folio_sync.upsert import suppress_by_guid

GUID = "1234"


class FakeInventory:
    """Records write order and returns a stored record for each entity hrid."""

    def __init__(self, existing: dict[str, dict[str, Any]] | None = None) -> None:
        # search_path -> the record to return for any hrid lookup (None = absent)
        self.existing = existing if existing is not None else {}
        self.put_calls: list[tuple[str, dict[str, Any]]] = []
        self.get_paths: list[str] = []

    def get(self, path: str, params: Mapping[str, Any] | None = None) -> dict[str, Any]:
        self.get_paths.append(path)
        record = self.existing.get(path)
        list_key = {
            "/inventory/items": "items",
            "/holdings-storage/holdings": "holdingsRecords",
            "/inventory/instances": "instances",
        }[path]
        return {list_key: [record] if record else []}

    def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        raise AssertionError("suppression must not POST")

    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        self.put_calls.append((path, dict(payload)))
        return {}

    def delete(self, path: str) -> dict[str, Any]:
        raise AssertionError("suppression must not hard-delete")


def _all_present() -> FakeInventory:
    return FakeInventory(
        {
            "/inventory/items": {"id": "item-1", "circulationNotes": ["ro"]},
            "/holdings-storage/holdings": {"id": "hold-1", "holdingsItems": ["ro"]},
            "/inventory/instances": {"id": "inst-1", "precedingTitles": ["ro"]},
        }
    )


def test_suppresses_all_three_entities_with_both_flags() -> None:
    folio = _all_present()

    result = suppress_by_guid(GUID, folio, dry_run=False)

    assert result.item.action == "suppress"
    assert result.holdings.action == "suppress"
    assert result.instance.action == "suppress"
    assert not result.errors

    for _, payload in folio.put_calls:
        assert payload["discoverySuppress"] is True
        assert payload["staffSuppress"] is True


def test_suppresses_child_first() -> None:
    folio = _all_present()

    suppress_by_guid(GUID, folio, dry_run=False)

    put_paths = [path for path, _ in folio.put_calls]
    assert put_paths == [
        "/inventory/items/item-1",
        "/holdings-storage/holdings/hold-1",
        "/inventory/instances/inst-1",
    ]


def test_strips_readonly_fields_before_put() -> None:
    folio = _all_present()

    suppress_by_guid(GUID, folio, dry_run=False)

    payload_by_path = {path: payload for path, payload in folio.put_calls}
    assert "circulationNotes" not in payload_by_path["/inventory/items/item-1"]
    assert "holdingsItems" not in payload_by_path["/holdings-storage/holdings/hold-1"]
    assert "precedingTitles" not in payload_by_path["/inventory/instances/inst-1"]


def test_missing_records_are_skipped_not_created() -> None:
    folio = FakeInventory({})  # nothing exists

    result = suppress_by_guid(GUID, folio, dry_run=False)

    assert result.item.action == "skip"
    assert result.holdings.action == "skip"
    assert result.instance.action == "skip"
    assert folio.put_calls == []
    assert not result.errors


def test_partial_presence_suppresses_only_found_records() -> None:
    folio = FakeInventory(
        {"/inventory/instances": {"id": "inst-1"}}  # only the instance survives
    )

    result = suppress_by_guid(GUID, folio, dry_run=False)

    assert result.item.action == "skip"
    assert result.holdings.action == "skip"
    assert result.instance.action == "suppress"
    assert [path for path, _ in folio.put_calls] == ["/inventory/instances/inst-1"]


def test_dry_run_plans_without_writing() -> None:
    folio = _all_present()

    result = suppress_by_guid(GUID, folio, dry_run=True)

    # Actions are still planned (resolved by hrid) but no PUT is issued.
    assert result.item.action == "suppress"
    assert result.instance.action == "suppress"
    assert folio.put_calls == []


def test_put_failure_is_captured_as_error() -> None:
    folio = _all_present()

    def boom(path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        raise RuntimeError("folio down")

    folio.put = boom  # type: ignore[method-assign]

    result = suppress_by_guid(GUID, folio, dry_run=False)

    assert result.errors
    assert result.errors[0].type == "api"
