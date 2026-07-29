"""Tests for the reconciler delete paths (``suppress_by_guid`` / ``delete_by_guid``).

A superseded GUID from the reconciler is an authoritative delete: the three
FOLIO records keyed on ``AxC-{entity}-{guid}`` are either soft-suppressed
(``discoverySuppress`` on all three, plus ``staffSuppress`` on the instance) or
hard-deleted, child-first.
"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from adapters.steps.axiell_folio_sync.upsert import delete_by_guid, suppress_by_guid

GUID = "1234"


class FakeInventory:
    """Records write order and returns a stored record for each entity hrid."""

    def __init__(self, existing: dict[str, dict[str, Any]] | None = None) -> None:
        # search_path -> the record to return for any hrid lookup (None = absent)
        self.existing = existing if existing is not None else {}
        self.put_calls: list[tuple[str, dict[str, Any]]] = []
        self.delete_paths: list[str] = []
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
        raise AssertionError("reconciler deletes must not POST")

    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        self.put_calls.append((path, dict(payload)))
        return {}

    def delete(self, path: str) -> dict[str, Any]:
        self.delete_paths.append(path)
        return {}


def _all_present() -> FakeInventory:
    return FakeInventory(
        {
            "/inventory/items": {"id": "item-1", "circulationNotes": ["ro"]},
            "/holdings-storage/holdings": {"id": "hold-1", "holdingsItems": ["ro"]},
            "/inventory/instances": {"id": "inst-1", "precedingTitles": ["ro"]},
        }
    )


def test_suppresses_all_three_entities_with_correct_flags() -> None:
    folio = _all_present()

    result = suppress_by_guid(GUID, folio, dry_run=False)

    assert result.item.action == "suppress"
    assert result.holdings.action == "suppress"
    assert result.instance.action == "suppress"
    assert not result.errors
    assert folio.delete_paths == []  # suppress is reversible, never hard-deletes

    # discoverySuppress on every entity; staffSuppress only on the instance,
    # the sole FOLIO inventory entity that carries that field (holdings-storage
    # 422s on it, items silently drop it).
    for path, payload in folio.put_calls:
        assert payload["discoverySuppress"] is True
        if path.startswith("/inventory/instances/"):
            assert payload["staffSuppress"] is True
        else:
            assert "staffSuppress" not in payload


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


# ── hard delete (delete_by_guid) ──────────────────────────────────────────────


def test_hard_delete_removes_all_three_child_first() -> None:
    folio = _all_present()

    result = delete_by_guid(GUID, folio, dry_run=False)

    assert result.item.action == "delete"
    assert result.holdings.action == "delete"
    assert result.instance.action == "delete"
    assert not result.errors
    assert folio.put_calls == []  # hard delete never suppresses
    assert folio.delete_paths == [
        "/inventory/items/item-1",
        "/holdings-storage/holdings/hold-1",
        "/inventory/instances/inst-1",
    ]


def test_hard_delete_skips_missing_records() -> None:
    folio = FakeInventory({})  # nothing exists

    result = delete_by_guid(GUID, folio, dry_run=False)

    assert result.item.action == "skip"
    assert result.holdings.action == "skip"
    assert result.instance.action == "skip"
    assert folio.delete_paths == []
    assert not result.errors


def test_hard_delete_dry_run_plans_without_writing() -> None:
    folio = _all_present()

    result = delete_by_guid(GUID, folio, dry_run=True)

    assert result.item.action == "delete"
    assert result.instance.action == "delete"
    assert folio.delete_paths == []


def test_hard_delete_aborts_cascade_when_a_child_fails() -> None:
    # FOLIO 400s the item delete (e.g. a loan still references it). The cascade
    # must stop before holdings/instance so a parent is never left orphaned.
    folio = _all_present()

    def boom(path: str) -> dict[str, Any]:
        raise RuntimeError("409 item still referenced")

    folio.delete = boom  # type: ignore[method-assign]

    result = delete_by_guid(GUID, folio, dry_run=False)

    assert result.errors
    assert result.errors[0].type == "api"
    # item op raised → holdings and instance were never attempted.
    assert result.holdings.action is None
    assert result.instance.action is None


def test_hard_delete_lookup_failure_is_an_error_not_a_skip() -> None:
    # A FOLIO outage makes the *lookup* raise. That is not "already gone": it must
    # surface as an error and abort the cascade, otherwise a still-live record is
    # reported as a clean skip/skip/skip deletion and never retried. In hard-delete
    # mode a swallowed item lookup would also let holdings/instance be deleted while
    # the item may still exist.
    folio = _all_present()

    def boom(path: str, params: Mapping[str, Any] | None = None) -> dict[str, Any]:
        raise RuntimeError("folio down")

    folio.get = boom  # type: ignore[method-assign]

    result = delete_by_guid(GUID, folio, dry_run=False)

    assert result.errors
    assert result.errors[0].type == "api"
    # item lookup raised → nothing was actioned, child-first abort holds.
    assert result.item.action is None
    assert result.holdings.action is None
    assert result.instance.action is None
    assert folio.delete_paths == []


def test_suppress_lookup_failure_is_an_error_not_a_skip() -> None:
    # Same outage on the soft-suppress path: a failed lookup must not be reported
    # as a successfully-actioned (skip) deletion.
    folio = _all_present()

    def boom(path: str, params: Mapping[str, Any] | None = None) -> dict[str, Any]:
        raise RuntimeError("folio down")

    folio.get = boom  # type: ignore[method-assign]

    result = suppress_by_guid(GUID, folio, dry_run=False)

    assert result.errors
    assert result.errors[0].type == "api"
    assert result.item.action is None
    assert result.holdings.action is None
    assert result.instance.action is None
    assert folio.put_calls == []
