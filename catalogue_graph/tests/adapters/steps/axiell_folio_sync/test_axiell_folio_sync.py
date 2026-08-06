"""Handler-level tests for the Axiell to Folio sync step.

These exercise how run_sync selects, skips, and processes rows using injected
fakes (a stub ref cache + FOLIO callables) rather than patching module globals.
"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

import pytest

import adapters.steps.axiell_folio_sync.run_axiell_folio_sync as run_sync_mod
from adapters.steps.axiell_folio_sync.folio.okapi import load_okapi_config
from adapters.steps.axiell_folio_sync.models import AxiellFolioSyncEvent
from adapters.steps.axiell_folio_sync.report import AxiellFolioSyncReport
from adapters.steps.axiell_folio_sync.results import (
    EntityResult,
    UpsertResult,
)
from adapters.steps.axiell_folio_sync.run_axiell_folio_sync import run_sync
from adapters.utils.axiell_changeset_reader import SupersededGuid

# 001 (guid), 980 $a (harvest flag), 351 $c (record type), 245 $a (title).
SELECTED = (
    "<record>"
    "<controlfield tag='001'>guid-1</controlfield>"
    "<datafield tag='980'><subfield code='a'>Y</subfield></datafield>"
    "<datafield tag='351'><subfield code='c'>ITEM</subfield></datafield>"
    "<datafield tag='245'><subfield code='a'>A Title</subfield></datafield>"
    "</record>"
)
# Item-level with a title but no 980 $a harvest flag -> not selected.
UNSELECTED = (
    "<record>"
    "<controlfield tag='001'>guid-2</controlfield>"
    "<datafield tag='351'><subfield code='c'>ITEM</subfield></datafield>"
    "<datafield tag='245'><subfield code='a'>Skip me</subfield></datafield>"
    "</record>"
)


class FakeRefCache:
    """Resolves every reference-data name to a stub UUID (no FOLIO calls)."""

    def instance_type_id(self) -> str:
        return "itype-uuid"

    def resolve_location(self, name: str | None) -> str:
        return "loc-uuid"

    def resolve_holdings_source(self, name: str | None) -> str:
        return "src-uuid"

    def resolve_material_type(self, name: str | None) -> str:
        return "mat-uuid"

    def resolve_loan_type(self, name: str | None) -> str:
        return "loan-uuid"

    def resolve_item_note_type(self, name: str | None) -> str:
        return "note-uuid"

    def resolve_identifier_type(self, name: str | None) -> str:
        return "idtype-uuid"


def _row(row_id: str, content: str) -> dict[str, Any]:
    return {"id": row_id, "changeset": "cs1", "content": content, "deleted": False}


def _folio_get(path: str, params: Mapping[str, Any] | None = None) -> dict[str, Any]:
    # Nothing exists yet -> every hrid lookup returns no records -> plan "create".
    return {}


def _no_write(*args: Any, **kwargs: Any) -> dict:
    raise AssertionError("dry-run must not issue FOLIO writes")


def _no_delete(*args: Any, **kwargs: Any) -> dict:
    raise AssertionError("dry-run must not issue FOLIO deletes")


class _FakeInventory:
    def get(self, path: str, params: Mapping[str, Any] | None = None) -> dict[str, Any]:
        return _folio_get(path, params)

    def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        return _no_write(path, payload)

    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        return _no_write(path, payload)

    def delete(self, path: str) -> dict[str, Any]:
        return _no_delete(path)


def _run(rows: list[dict[str, Any]]) -> Any:
    return run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        rows,
        FakeRefCache(),  # type: ignore[arg-type]
        _FakeInventory(),
        dry_run=True,
    )


def test_processes_all_records_selection_gate_disabled() -> None:
    # Selection gate is disabled ("run for all"): the record without a 980 $a harvest
    # flag is no longer skipped — both rows are built and upserted.
    resp = _run([_row("sel", SELECTED), _row("unsel", UNSELECTED)])

    assert resp.counts["total"] == 2
    assert resp.counts["skipped"] == 0  # nothing skipped now
    assert resp.total_successful == 2  # both planned an upsert
    assert resp.counts["created"] == 6  # 2 records x (instance + holdings + item)
    assert resp.total_errors == 0


def test_malformed_xml_recorded_as_error() -> None:
    resp = _run([_row("bad", "<record><oops")])

    assert resp.counts["failed"] == 1
    assert resp.total_errors == 1
    assert resp.counts["skipped"] == 0


def test_loader_tombstone_is_advisory_not_suppressed() -> None:
    # deleted=true is recorded as an advisory signal, not suppressed/upserted.
    tombstone = {**_row("tomb", SELECTED), "deleted": True}
    resp = _run([tombstone])

    assert resp.counts["tombstone"] == 1
    assert resp.counts["suppressed"] == 0
    assert resp.counts["created"] == 0  # no upsert happened
    assert resp.total_successful == 0
    assert resp.total_errors == 0


def test_load_okapi_config_raises_clear_error_for_missing_fields(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    for key in (
        "OKAPI_URL",
        "OKAPI_TENANT",
        "OKAPI_USERNAME",
        "OKAPI_PASSWORD",
        "OKAPI_SECRET_PARAM",
    ):
        monkeypatch.delenv(key, raising=False)

    with pytest.raises(ValueError, match="Missing OKAPI configuration fields"):
        load_okapi_config()


def test_report_written_to_s3_on_dry_run(monkeypatch: pytest.MonkeyPatch) -> None:
    # Dry runs still publish the S3 report (that's how they're validated);
    # only CloudWatch metrics are suppressed.
    captured: dict[str, Any] = {}

    def fake_to_s3(model: Any, s3_uri: str) -> None:
        captured["model"] = model
        captured["s3_uri"] = s3_uri

    monkeypatch.setattr("utils.reporting.pydantic_to_s3_json", fake_to_s3)

    resp = run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [_row("sel", SELECTED), _row("bad", "<record><oops")],
        FakeRefCache(),  # type: ignore[arg-type]
        _FakeInventory(),
        dry_run=True,
        manifest_bucket="bucket-1",
    )

    assert captured["s3_uri"] == "s3://bucket-1/manifests/job-1.json"
    assert resp.manifest_s3_path == captured["s3_uri"]

    report = captured["model"]
    assert report.publish_to_cloudwatch is False  # dry run suppresses metrics
    assert [entry.source_id for entry in report.successful] == ["sel"]
    assert report.successful[0].instance_action == "create"
    assert [entry.source_id for entry in report.errors] == ["bad"]
    assert report.errors[0].stage == "selection"
    assert report.counts == resp.counts


def test_upsert_errors_recorded_as_structured_entries(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # The upsert stage reports a list of per-entity error dicts, exercising the
    # list branch of SyncErrorEntry.error (other stages use the str branch).
    from adapters.steps.axiell_folio_sync.results import UpsertError

    def failing_upsert(*args: Any, **kwargs: Any) -> UpsertResult:
        return UpsertResult(
            source_id="sel",
            mapping_version="2.1.0",
            instance=EntityResult(action=None),
            holdings=EntityResult(action=None),
            item=EntityResult(action=None),
            errors=[UpsertError(type="instance_put_failed", detail="FOLIO said no")],
        )

    monkeypatch.setattr(run_sync_mod, "upsert_from_payloads",failing_upsert)

    captured: dict[str, Any] = {}
    monkeypatch.setattr(
        "utils.reporting.pydantic_to_s3_json",
        lambda model, s3_uri: captured.update(model=model),
    )

    resp = run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [_row("sel", SELECTED)],
        FakeRefCache(),  # type: ignore[arg-type]
        _FakeInventory(),
        dry_run=True,
        manifest_bucket="bucket-1",
    )

    assert resp.total_errors == 1
    entry = captured["model"].errors[0]
    assert entry.stage == "upsert"
    assert isinstance(entry.error, list)
    assert entry.error[0]["detail"] == "FOLIO said no"


def test_no_s3_report_without_bucket(monkeypatch: pytest.MonkeyPatch) -> None:
    def fail_to_s3(model: Any, s3_uri: str) -> None:
        raise AssertionError("must not publish to S3 without a bucket")

    monkeypatch.setattr("utils.reporting.pydantic_to_s3_json", fail_to_s3)

    resp = _run([_row("sel", SELECTED)])

    assert resp.manifest_s3_path is None


def test_no_s3_report_with_empty_string_bucket(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def fail_to_s3(model: Any, s3_uri: str) -> None:
        raise AssertionError("must not publish to S3 with an empty bucket name")

    monkeypatch.setattr("utils.reporting.pydantic_to_s3_json", fail_to_s3)

    resp = run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [_row("sel", SELECTED)],
        FakeRefCache(),  # type: ignore[arg-type]
        _FakeInventory(),
        dry_run=True,
        manifest_bucket="",
    )

    assert resp.manifest_s3_path is None


def test_report_s3_uri_requires_bucket() -> None:
    report = AxiellFolioSyncReport(job_id="job-1", dry_run=True, counts={})

    with pytest.raises(ValueError, match="No S3 bucket configured"):
        _ = report.s3_uri


def test_run_sync_passes_ref_cache_to_upsert(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, Any] = {}

    def fake_upsert(
        mapped: Any,
        folio: Any,
        *,
        ref_cache: Any = None,
        dry_run: bool = False,
    ) -> UpsertResult:
        captured["ref_cache"] = ref_cache
        return UpsertResult(
            source_id="sel",
            mapping_version="2.1.0",
            instance=EntityResult(action="create"),
            holdings=EntityResult(action="create"),
            item=EntityResult(action="create"),
        )

    monkeypatch.setattr(run_sync_mod, "upsert_from_payloads",fake_upsert)

    run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [_row("sel", SELECTED)],
        FakeRefCache(),  # type: ignore[arg-type]
        _FakeInventory(),
        dry_run=True,
    )

    assert isinstance(captured["ref_cache"], FakeRefCache)


# ── Pass 2: reconciler deletions (superseded GUIDs) ───────────────────────────


class _SuppressInventory:
    """FOLIO fake where every entity for a GUID already exists and can be PUT."""

    def __init__(self) -> None:
        self.put_paths: list[str] = []

    def get(self, path: str, params: Mapping[str, Any] | None = None) -> dict[str, Any]:
        list_key = {
            "/inventory/items": "items",
            "/holdings-storage/holdings": "holdingsRecords",
            "/inventory/instances": "instances",
        }.get(path)
        return {list_key: [{"id": f"{path}#id"}]} if list_key else {}

    def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        raise AssertionError("suppression must not POST")

    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        self.put_paths.append(path)
        return {}

    def delete(self, path: str) -> dict[str, Any]:
        raise AssertionError("suppression must not hard-delete")


def _superseded(guid: str) -> SupersededGuid:
    from datetime import UTC, datetime

    return SupersededGuid(
        fact_id=f"rec/{guid}/cs1",
        record_id=f"rec-{guid}",
        guid=guid,
        changeset_id="cs1",
        last_modified=datetime(2026, 1, 1, tzinfo=UTC),
    )


def test_deletions_suppress_and_are_reported() -> None:
    folio = _SuppressInventory()

    resp = run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [],  # no upsert rows, deletions only
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=False,
        deletions=[_superseded("g1"), _superseded("g2")],
    )

    assert resp.total_deletions == 2
    assert resp.counts["deletions"] == 2
    # 2 guids x 3 entities each suppressed.
    assert resp.counts["suppressed"] == 6
    assert resp.total_errors == 0
    # 2 guids x 3 entities each PUT back suppressed.
    assert len(folio.put_paths) == 6
    # Cleanly-actioned deletions count toward the totals symmetrically with the
    # upsert pass: a deletions-only run is not reported as zero work.
    assert resp.total_successful == 2
    assert resp.total_records == resp.total_successful + resp.total_errors == 2


def test_deletion_failure_recorded_as_error_without_aborting() -> None:
    folio = _SuppressInventory()

    def boom(path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        raise RuntimeError("folio down")

    folio.put = boom  # type: ignore[method-assign]

    resp = run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [],
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=False,
        deletions=[_superseded("g1")],
    )

    assert resp.counts["deletions"] == 1
    assert resp.counts["suppressed"] == 0
    assert resp.total_errors == 1
    # An errored GUID is not also counted as a success.
    assert resp.total_successful == 0
    assert resp.total_records == 1


def test_no_deletions_leaves_counts_zero() -> None:
    resp = _run([_row("sel", SELECTED)])

    assert resp.total_deletions == 0
    assert resp.counts["deletions"] == 0


class _DeleteInventory:
    """FOLIO fake where every entity exists and DELETE is allowed/recorded."""

    def __init__(self) -> None:
        self.delete_paths: list[str] = []
        self.put_paths: list[str] = []

    def get(self, path: str, params: Mapping[str, Any] | None = None) -> dict[str, Any]:
        list_key = {
            "/inventory/items": "items",
            "/holdings-storage/holdings": "holdingsRecords",
            "/inventory/instances": "instances",
        }.get(path)
        return {list_key: [{"id": f"{path}#id"}]} if list_key else {}

    def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        raise AssertionError("must not POST")

    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        self.put_paths.append(path)
        return {}

    def delete(self, path: str) -> dict[str, Any]:
        self.delete_paths.append(path)
        return {}


def test_hard_delete_mode_deletes_instead_of_suppressing() -> None:
    folio = _DeleteInventory()

    resp = run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [],
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=False,
        deletions=[_superseded("g1")],
        hard_delete=True,
    )

    assert resp.counts["deletions"] == 1
    assert resp.counts["deleted"] == 3  # instance + holdings + item
    assert resp.counts["suppressed"] == 0
    assert resp.total_errors == 0
    assert folio.put_paths == []  # hard delete never suppresses
    assert len(folio.delete_paths) == 3


def test_partial_hard_delete_cascade_still_counts_the_executed_child() -> None:
    # Cascade is item → holdings → instance. The item is irreversibly deleted,
    # then holdings fails. The already-executed item delete must appear in the
    # counts (and manifest) rather than vanishing behind the GUID-level error.
    folio = _DeleteInventory()

    def delete_but_fail_holdings(path: str) -> dict[str, Any]:
        if path.startswith("/holdings-storage/holdings"):
            raise RuntimeError("FOLIO 500 deleting holdings")
        folio.delete_paths.append(path)
        return {}

    folio.delete = delete_but_fail_holdings  # type: ignore[method-assign]

    resp = run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [],
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=False,
        deletions=[_superseded("g1")],
        hard_delete=True,
    )

    assert resp.counts["deletions"] == 1
    assert resp.total_errors == 1  # the holdings failure is recorded
    assert resp.counts["deleted"] == 1  # ...but the item delete is not lost
    # Only the item was deleted; holdings failed and instance was never reached.
    assert len(folio.delete_paths) == 1
    assert folio.delete_paths[0].startswith("/inventory/items")


def test_hard_delete_failure_is_reported_under_delete_stage(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # A failed hard delete must be recorded with stage="delete", not "suppress",
    # so operators can tell an irreversible-delete failure from a suppression one.
    folio = _DeleteInventory()

    def boom(path: str) -> dict[str, Any]:
        raise RuntimeError("FOLIO 500 deleting item")

    folio.delete = boom  # type: ignore[method-assign]

    captured: dict[str, Any] = {}
    monkeypatch.setattr(
        "utils.reporting.pydantic_to_s3_json",
        lambda model, s3_uri: captured.update(model=model),
    )

    resp = run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [],
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=False,
        deletions=[_superseded("g1")],
        hard_delete=True,
        manifest_bucket="bucket-1",
    )

    assert resp.total_errors == 1
    assert captured["model"].errors[0].stage == "delete"


@pytest.mark.parametrize(
    ("current_location", "expected"),
    [
        ("215", "hicon"),
        ("215-3", "hicon"),
        ("  183abc", "hicon"),
        ("183", "hicon"),
        ("21", "21"),  # too short to match the 215 prefix
        ("STACK", "STACK"),  # unrelated code passes through unchanged
        ("", ""),
        (None, None),
    ],
)
def test_folio_location_prefix_override(
    current_location: str | None, expected: str | None
) -> None:
    from adapters.steps.axiell_folio_sync.mapping import _folio_location

    assert _folio_location(current_location) == expected


def test_object_number_maps_to_local_identifier() -> None:
    # AxC object_number (035$a) becomes a "Local identifier" on the instance.
    from adapters.steps.axiell_folio_sync.mapping import (
        CanonicalRecord,
        build_instance,
    )

    rec = CanonicalRecord(source_id="g1", title="A Title", object_number=" AxC-12345 ")
    instance = build_instance(rec, FakeRefCache())  # type: ignore[arg-type]

    assert instance.identifiers is not None
    assert len(instance.identifiers) == 1
    identifier = instance.identifiers[0]
    assert identifier.value == "AxC-12345"  # trimmed
    assert identifier.identifierTypeId == "idtype-uuid"


def test_no_object_number_yields_no_identifiers() -> None:
    # 035$a absent (or blank) -> identifiers omitted entirely, not an empty list.
    from adapters.steps.axiell_folio_sync.mapping import (
        CanonicalRecord,
        build_instance,
    )

    for object_number in (None, "", "   "):
        rec = CanonicalRecord(
            source_id="g1", title="A Title", object_number=object_number
        )
        instance = build_instance(rec, FakeRefCache())  # type: ignore[arg-type]
        assert instance.identifiers is None


def test_material_type_lookup_is_case_insensitive() -> None:
    # AxC object_category values carry their own (mixed) case; the field's lookup
    # table folds keys to lowercase so a cased source value still resolves. Guard
    # against the fold being dropped (which would silently strand every entry).
    from adapters.steps.axiell_folio_sync.mapping import (
        MATERIAL_TYPE,
        MATERIAL_TYPE_FIELD,
    )

    assert MATERIAL_TYPE_FIELD.table is not None
    assert all(key == key.lower() for key in MATERIAL_TYPE_FIELD.table)
    for source_value, folio_name in MATERIAL_TYPE.items():
        # Every entry is reachable via the lowercased incoming value.
        assert MATERIAL_TYPE_FIELD.table[source_value.lower()] == folio_name
