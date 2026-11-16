"""Tests for acting on Collections tombstones (wellcomecollection/platform#6833).

A record hard deleted in Collections arrives as an OAI-PMH tombstone with its
content preserved. The read step derives its GUID from that content and drops
any GUID another record still holds; ``run_sync`` then deletes (or suppresses)
the GUID's FOLIO records in its second pass, child first.
"""

from __future__ import annotations

from collections.abc import Mapping
from datetime import UTC, datetime
from typing import Any

import pytest

from adapters.steps.axiell_folio_sync.axiell_adapter_read import (
    TombstonedGuid,
    tombstoned_guids,
)
from adapters.steps.axiell_folio_sync.mapping import (
    _holdings_id,
    _instance_id,
    _item_id,
)
from adapters.steps.axiell_folio_sync.models import AxiellFolioSyncEvent
from adapters.steps.axiell_folio_sync.run_axiell_folio_sync import run_sync
from adapters.utils.axiell_changeset_reader import SupersededGuid

from .test_axiell_folio_sync import SELECTED, FakeRefCache

GUID = "00000000-0000-4000-8000-000000000001"
OTHER_GUID = "00000000-0000-4000-8000-000000000002"

_LIST_KEYS = {
    "/inventory/items": "items",
    "/holdings-storage/holdings": "holdingsRecords",
    "/inventory/instances": "instances",
}


class _Inventory:
    """FOLIO fake holding the three records the sync made for each synced GUID.

    Lookups answer by id, so a GUID that was never synced finds nothing.
    Writes are recorded in order, so the cascade order can be asserted.
    """

    def __init__(self, synced_guids: list[str]) -> None:
        self.existing = {
            folio_id
            for guid in synced_guids
            for folio_id in (_item_id(guid), _holdings_id(guid), _instance_id(guid))
        }
        self.deleted: list[str] = []
        self.put_paths: list[str] = []
        self.fail_delete_prefix: str | None = None

    def get(self, path: str, params: Mapping[str, Any] | None = None) -> dict[str, Any]:
        assert params is not None
        folio_id = params["query"].split('"')[1]
        records = [{"id": folio_id}] if folio_id in self.existing else []
        return {_LIST_KEYS[path]: records}

    def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        raise AssertionError("a tombstone must not POST")

    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        self.put_paths.append(path)
        return {}

    def delete(self, path: str) -> dict[str, Any]:
        if self.fail_delete_prefix and path.startswith(self.fail_delete_prefix):
            raise RuntimeError(f"FOLIO 500 deleting {path}")
        self.deleted.append(path)
        return {}


def _tombstone(guid: str = GUID) -> TombstonedGuid:
    return TombstonedGuid(
        record_id=f"collect-{guid[-1]}", guid=guid, changeset_id="cs1"
    )


def _sync(
    folio: _Inventory,
    *,
    tombstones: list[TombstonedGuid],
    deletions: list[SupersededGuid] | None = None,
    hard_delete: bool = True,
) -> Any:
    return run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [],
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=False,
        deletions=deletions,
        tombstones=tombstones,
        hard_delete=hard_delete,
    )


# -- run_sync: the second pass --


def test_tombstone_hard_deletes_all_three_records_child_first() -> None:
    folio = _Inventory(synced_guids=[GUID])

    resp = _sync(folio, tombstones=[_tombstone()])

    assert folio.deleted == [
        f"/inventory/items/{_item_id(GUID)}",
        f"/holdings-storage/holdings/{_holdings_id(GUID)}",
        f"/inventory/instances/{_instance_id(GUID)}",
    ]
    assert folio.put_paths == []
    assert resp.counts["deletions"] == 1
    assert resp.counts["tombstone_deletions"] == 1
    assert resp.counts["deleted"] == 3
    assert resp.total_successful == 1
    assert resp.total_errors == 0


def test_tombstone_is_reported_with_its_reason(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, Any] = {}
    monkeypatch.setattr(
        "utils.reporting.pydantic_to_s3_json",
        lambda model, s3_uri: captured.update(model=model),
    )

    run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [],
        FakeRefCache(),  # type: ignore[arg-type]
        _Inventory(synced_guids=[GUID]),
        dry_run=False,
        tombstones=[_tombstone()],
        hard_delete=True,
        manifest_bucket="bucket-1",
    )

    [entry] = captured["model"].deletions
    assert entry.reason == "tombstone"
    assert entry.guid == GUID
    assert entry.record_id == "collect-1"
    assert (entry.item_action, entry.holdings_action, entry.instance_action) == (
        "delete",
        "delete",
        "delete",
    )


def test_tombstone_for_a_record_never_synced_is_a_no_op() -> None:
    folio = _Inventory(synced_guids=[])

    resp = _sync(folio, tombstones=[_tombstone()])

    assert folio.deleted == []
    assert resp.counts["deletions"] == 1
    assert resp.counts["deleted"] == 0
    assert resp.total_errors == 0


def test_failed_child_delete_leaves_the_parent_in_place() -> None:
    folio = _Inventory(synced_guids=[GUID])
    folio.fail_delete_prefix = "/holdings-storage/holdings"

    resp = _sync(folio, tombstones=[_tombstone()])

    # The item went, the holdings failed, and the instance was never reached.
    assert folio.deleted == [f"/inventory/items/{_item_id(GUID)}"]
    assert resp.counts["deleted"] == 1
    assert resp.total_errors == 1


def test_tombstone_is_suppressed_when_hard_delete_is_off() -> None:
    folio = _Inventory(synced_guids=[GUID])

    resp = _sync(folio, tombstones=[_tombstone()], hard_delete=False)

    assert folio.deleted == []
    assert len(folio.put_paths) == 3
    assert resp.counts["suppressed"] == 3
    # A second-pass suppression is not attributed to an untick.
    assert resp.counts["unflagged_suppressed"] == 0


def test_guid_both_superseded_and_tombstoned_is_actioned_once() -> None:
    folio = _Inventory(synced_guids=[GUID])
    superseded = SupersededGuid(
        fact_id="collect-1/cs1",
        record_id="collect-1",
        guid=GUID,
        changeset_id="cs1",
        last_modified=datetime(2026, 1, 1, tzinfo=UTC),
    )

    resp = _sync(folio, tombstones=[_tombstone()], deletions=[superseded])

    assert len(folio.deleted) == 3
    assert resp.counts["deletions"] == 1
    assert resp.counts["tombstone_deletions"] == 0


# -- tombstoned_guids: deriving the GUIDs from adapter rows --


def _adapter_row(
    record_id: str, content: str | None, *, deleted: bool
) -> dict[str, Any]:
    return {
        "id": record_id,
        "changeset": "cs1",
        "content": content,
        "deleted": deleted,
        "last_modified": datetime(2026, 1, 1, tzinfo=UTC),
    }


def _nothing_held_elsewhere(guid_by_record_id: Mapping[str, str]) -> set[str]:
    return set()


def test_tombstoned_guid_comes_from_the_preserved_001() -> None:
    rows = [
        _adapter_row("collect-1", SELECTED, deleted=True),
        _adapter_row("collect-2", SELECTED.replace(GUID, OTHER_GUID), deleted=False),
    ]

    assert tombstoned_guids(rows, _nothing_held_elsewhere) == [
        TombstonedGuid(record_id="collect-1", guid=GUID, changeset_id="cs1")
    ]


def test_tombstone_without_content_is_passed_over() -> None:
    rows = [_adapter_row("collect-1", None, deleted=True)]

    assert tombstoned_guids(rows, _nothing_held_elsewhere) == []


def test_guid_held_by_another_record_is_not_deleted() -> None:
    rows = [_adapter_row("collect-1", SELECTED, deleted=True)]
    asked: list[Mapping[str, str]] = []

    def held_elsewhere(guid_by_record_id: Mapping[str, str]) -> set[str]:
        asked.append(guid_by_record_id)
        return {GUID}

    assert tombstoned_guids(rows, held_elsewhere) == []
    assert asked == [{"collect-1": GUID}]
