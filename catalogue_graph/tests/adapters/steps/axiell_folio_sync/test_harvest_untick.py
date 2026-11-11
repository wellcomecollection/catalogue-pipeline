"""Unticking and re-ticking Harvest (MARC 980 $a) on a record that has synced.

Unticking suppresses the record's FOLIO instance, holdings and item. Ticking it
again re-attaches to the same three records, found by the ids derived from the
GUID, and clears the suppression, so nothing new is created. Decided on
wellcomecollection/platform#6663.

These run against an in-memory FOLIO fake, not the sandbox.
"""

from __future__ import annotations

from collections.abc import Mapping
from datetime import UTC, datetime
from typing import Any

import pytest

from adapters.steps.axiell_folio_sync.mapping import (
    _holdings_id,
    _instance_id,
    _item_id,
    unflagged_guid,
)
from adapters.steps.axiell_folio_sync.models import AxiellFolioSyncEvent
from adapters.steps.axiell_folio_sync.report import AxiellFolioSyncReport
from adapters.steps.axiell_folio_sync.run_axiell_folio_sync import run_sync
from adapters.utils.axiell_changeset_reader import SupersededGuid

from .test_axiell_folio_sync import FakeRefCache

GUID = "00000000-0000-4000-8000-000000000001"

_LIST_KEYS = {
    "/inventory/instances": "instances",
    "/holdings-storage/holdings": "holdingsRecords",
    "/inventory/items": "items",
}


def _record(*, flagged: bool, level: str = "ITEM", guid: str = GUID) -> str:
    """An Axiell record that maps cleanly, with or without the harvest flag."""
    flag = (
        "<datafield tag='980'><subfield code='a'>Y</subfield></datafield>"
        if flagged
        else ""
    )
    return (
        "<record>"
        f"<controlfield tag='001'>{guid}</controlfield>"
        f"{flag}"
        f"<datafield tag='351'><subfield code='c'>{level}</subfield></datafield>"
        "<datafield tag='245'><subfield code='a'>A Title</subfield></datafield>"
        "<datafield tag='655'><subfield code='a'>Archives - Non-digital</subfield></datafield>"
        "<datafield tag='984'><subfield code='b'>215/HOME</subfield></datafield>"
        "</record>"
    )


class StatefulInventory:
    """A FOLIO fake that keeps records by id, so runs can follow one another."""

    def __init__(self) -> None:
        # search/write path -> id -> record
        self.records: dict[str, dict[str, dict[str, Any]]] = {
            path: {} for path in _LIST_KEYS
        }
        self.posts: list[str] = []
        self.puts: list[str] = []
        self.gets: list[str] = []

    def get(self, path: str, params: Mapping[str, Any] | None = None) -> dict[str, Any]:
        self.gets.append(path)
        query = (params or {})["query"]
        folio_id = query.split('"')[1]
        record = self.records[path].get(folio_id)
        return {_LIST_KEYS[path]: [dict(record)] if record else []}

    def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        self.posts.append(path)
        self.records[path][payload["id"]] = dict(payload)
        return dict(payload)

    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        self.puts.append(path)
        collection, folio_id = path.rsplit("/", 1)
        self.records[collection][folio_id] = dict(payload)
        return {}

    def delete(self, path: str) -> dict[str, Any]:
        raise AssertionError("unticking must suppress, never delete")

    def instance(self, guid: str = GUID) -> dict[str, Any]:
        return self.records["/inventory/instances"][_instance_id(guid)]

    def holdings(self, guid: str = GUID) -> dict[str, Any]:
        return self.records["/holdings-storage/holdings"][_holdings_id(guid)]

    def item(self, guid: str = GUID) -> dict[str, Any]:
        return self.records["/inventory/items"][_item_id(guid)]

    def count(self) -> int:
        return sum(len(records) for records in self.records.values())


def _sync(
    folio: StatefulInventory,
    content: str,
    *,
    dry_run: bool = False,
    hard_delete: bool = False,
) -> Any:
    return run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [{"id": "rec-1", "changeset": "cs1", "content": content, "deleted": False}],
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=dry_run,
        hard_delete=hard_delete,
    )


def _synced() -> StatefulInventory:
    folio = StatefulInventory()
    resp = _sync(folio, _record(flagged=True))
    assert resp.counts["created"] == 3
    return folio


def _assert_suppressed(folio: StatefulInventory) -> None:
    assert folio.instance()["discoverySuppress"] is True
    assert folio.instance()["staffSuppress"] is True
    assert folio.holdings()["discoverySuppress"] is True
    assert folio.item()["discoverySuppress"] is True
    # Only the instance carries staffSuppress: holdings-storage 422s on it.
    assert "staffSuppress" not in folio.holdings()
    assert "staffSuppress" not in folio.item()


def _assert_unsuppressed(folio: StatefulInventory) -> None:
    assert folio.instance()["discoverySuppress"] is False
    assert folio.instance()["staffSuppress"] is False
    assert folio.holdings()["discoverySuppress"] is False
    assert folio.item()["discoverySuppress"] is False
    assert "staffSuppress" not in folio.holdings()
    assert "staffSuppress" not in folio.item()


# -- untick --


def test_a_new_record_is_created_unsuppressed() -> None:
    _assert_unsuppressed(_synced())


def test_untick_suppresses_all_three_records() -> None:
    folio = _synced()

    resp = _sync(folio, _record(flagged=False))

    _assert_suppressed(folio)
    assert resp.counts["unflagged"] == 1
    assert resp.counts["suppressed"] == 3
    assert resp.counts["unflagged_suppressed"] == 3
    assert resp.counts["unflagged_never_synced"] == 0
    assert resp.counts["skipped"] == 0
    assert resp.total_successful == 1
    assert resp.total_errors == 0
    # Suppressed in place: nothing created, nothing deleted.
    assert folio.count() == 3


def test_untick_suppressions_are_counted_apart_from_second_pass_ones(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, Any] = {}
    monkeypatch.setattr(
        "utils.reporting.pydantic_to_s3_json",
        lambda model, s3_uri: captured.update(model=model),
    )
    other = "00000000-0000-4000-8000-000000000002"
    folio = _synced()
    _sync(folio, _record(flagged=True, guid=other))

    resp = run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [
            {
                "id": "rec-1",
                "changeset": "cs1",
                "content": _record(flagged=False),
                "deleted": False,
            }
        ],
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=False,
        manifest_bucket="bucket-1",
        deletions=[
            SupersededGuid(
                fact_id="rec-2/cs1",
                record_id="rec-2",
                guid=other,
                changeset_id="cs1",
                last_modified=datetime(2026, 1, 1, tzinfo=UTC),
            )
        ],
    )

    # Both causes land in "suppressed"; only the untick's in
    # "unflagged_suppressed".
    assert resp.counts["suppressed"] == 6
    assert resp.counts["unflagged_suppressed"] == 3
    report: AxiellFolioSyncReport = captured["model"]
    metrics = {metric.name: metric.value for metric in report.metrics}
    assert metrics["records_suppressed"] == 6
    assert metrics["records_unflagged_suppressed"] == 3


def test_untick_is_reported_in_the_manifest(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, Any] = {}
    monkeypatch.setattr(
        "utils.reporting.pydantic_to_s3_json",
        lambda model, s3_uri: captured.update(model=model),
    )
    folio = _synced()

    run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [
            {
                "id": "rec-1",
                "changeset": "cs1",
                "content": _record(flagged=False),
                "deleted": False,
            }
        ],
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=False,
        manifest_bucket="bucket-1",
    )

    [entry] = captured["model"].deletions
    assert entry.reason == "unflagged"
    assert entry.guid == GUID
    assert entry.record_id == "rec-1"
    assert (entry.instance_action, entry.holdings_action, entry.item_action) == (
        "suppress",
        "suppress",
        "suppress",
    )


def test_untick_on_a_record_that_never_synced_is_a_no_op() -> None:
    folio = StatefulInventory()

    resp = _sync(folio, _record(flagged=False))

    assert folio.posts == [] and folio.puts == []
    # Three lookups for nothing, counted so the cost is visible.
    assert len(folio.gets) == 3
    assert resp.counts["unflagged"] == 1
    assert resp.counts["unflagged_never_synced"] == 1
    assert resp.counts["skipped"] == 1
    assert resp.counts["suppressed"] == 0
    assert resp.counts["unflagged_suppressed"] == 0
    assert resp.total_successful == 0
    assert resp.total_errors == 0


def test_untick_suppresses_even_in_hard_delete_mode() -> None:
    # HARD_DELETE governs superseded GUIDs and tombstones. An untick is always
    # reversible, so the record can come back when Harvest is ticked again.
    folio = _synced()

    resp = _sync(folio, _record(flagged=False), hard_delete=True)

    _assert_suppressed(folio)
    assert resp.counts["deleted"] == 0


def test_untick_dry_run_plans_without_writing() -> None:
    folio = _synced()
    puts_before = list(folio.puts)

    resp = _sync(folio, _record(flagged=False), dry_run=True)

    assert folio.puts == puts_before
    assert resp.counts["suppressed"] == 3
    _assert_unsuppressed(folio)


def test_untick_failure_is_an_error_under_the_suppress_stage(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, Any] = {}
    monkeypatch.setattr(
        "utils.reporting.pydantic_to_s3_json",
        lambda model, s3_uri: captured.update(model=model),
    )
    folio = _synced()

    def boom(path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        raise RuntimeError("folio down")

    folio.put = boom  # type: ignore[method-assign]

    resp = run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [
            {
                "id": "rec-1",
                "changeset": "cs1",
                "content": _record(flagged=False),
                "deleted": False,
            }
        ],
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=False,
        manifest_bucket="bucket-1",
    )

    assert resp.counts["failed"] == 1
    assert resp.total_errors == 1
    assert resp.total_successful == 0
    assert resp.counts["unflagged_never_synced"] == 0
    [error] = captured["model"].errors
    assert error.stage == "suppress"
    assert error.source_id == GUID


def test_a_record_that_is_not_item_level_is_skipped_without_lookups() -> None:
    # A change of level is not treated as an untick: the record is skipped and
    # FOLIO is not consulted, with or without the harvest flag.
    folio = _synced()
    gets_before = len(folio.gets)

    for flagged in (True, False):
        resp = _sync(folio, _record(flagged=flagged, level="Series"))
        assert resp.counts["skipped"] == 1
        assert resp.counts["unflagged"] == 0

    assert len(folio.gets) == gets_before
    _assert_unsuppressed(folio)


# -- re-tick --


def test_retick_unsuppresses_the_same_three_records() -> None:
    folio = _synced()
    ids_before = {path: set(records) for path, records in folio.records.items()}
    _sync(folio, _record(flagged=False))
    _assert_suppressed(folio)
    posts_before = len(folio.posts)

    resp = _sync(folio, _record(flagged=True))

    _assert_unsuppressed(folio)
    assert resp.counts["updated"] == 3
    assert resp.counts["created"] == 0
    assert len(folio.posts) == posts_before
    assert {path: set(records) for path, records in folio.records.items()} == (
        ids_before
    )


def test_an_update_clears_a_suppression_set_in_folio() -> None:
    # FOLIO does not record who suppressed a record, so a suppression set by
    # hand on a selected record is cleared on its next update too.
    folio = _synced()
    folio.instance()["discoverySuppress"] = True
    folio.instance()["staffSuppress"] = True

    _sync(folio, _record(flagged=True))

    _assert_unsuppressed(folio)


# -- unflagged_guid --


def test_unflagged_guid_is_the_001_of_an_unflagged_item() -> None:
    assert unflagged_guid(_record(flagged=False)) == GUID


@pytest.mark.parametrize(
    "content",
    [
        _record(flagged=True),
        _record(flagged=False, level="Series"),
        _record(flagged=False, guid="not-a-uuid"),
    ],
    ids=["selected", "not item-level", "001 not a UUID"],
)
def test_unflagged_guid_is_none_otherwise(content: str) -> None:
    assert unflagged_guid(content) is None
