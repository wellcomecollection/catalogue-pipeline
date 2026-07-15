"""Handler-level tests for the Axiell to Folio sync step.

These exercise how run_sync selects, skips, and processes rows using injected
fakes (a stub ref cache + FOLIO callables) rather than patching module globals.
"""

from __future__ import annotations

import io
from collections.abc import Mapping
from typing import Any

import pytest

import adapters.steps.axiell_folio_sync.sync_to_folio as sync_to_folio_mod
from adapters.steps.axiell_folio_sync.models import AxiellFolioSyncEvent
from adapters.steps.axiell_folio_sync.sync_to_folio import load_okapi_config, run_sync
from adapters.transformers.axiell_folio_sync.mapping import (
    EntityResult,
    UpsertResult,
)

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


def test_skips_unselected_and_upserts_selected() -> None:
    resp = _run([_row("sel", SELECTED), _row("unsel", UNSELECTED)])

    assert resp.counts["total"] == 2
    assert resp.counts["skipped"] == 1  # UNSELECTED has no 980 $a
    assert resp.total_successful == 1  # SELECTED planned an upsert
    assert resp.counts["created"] == 3  # instance + holdings + item (dry-run plan)
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


class _MockS3:
    def __init__(self) -> None:
        self.objects: dict[tuple[str, str], bytes] = {}

    def get_object(self, *, Bucket: str, Key: str) -> dict[str, Any]:
        from botocore.exceptions import ClientError

        content = self.objects.get((Bucket, Key))
        if content is None:
            raise ClientError({"Error": {"Code": "NoSuchKey"}}, "GetObject")
        return {"Body": io.BytesIO(content)}

    def put_object(
        self,
        *,
        Bucket: str,
        Key: str,
        Body: bytes,
        ContentType: str,
    ) -> dict[str, Any]:
        self.objects[(Bucket, Key)] = Body
        return {}


def test_flush_success_batch_appends_instead_of_overwrite(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    mock_s3 = _MockS3()
    import adapters.steps.axiell_folio_sync.s3_manifest as manifest_mod

    manifest_mod.flush_success_batch(mock_s3, "bucket-1", "job-1", [{"sourceId": "a"}])
    manifest_mod.flush_success_batch(mock_s3, "bucket-1", "job-1", [{"sourceId": "b"}])

    key = ("bucket-1", "manifests/job-1.ids.ndjson")
    payload = mock_s3.objects[key].decode("utf-8").strip().splitlines()
    assert len(payload) == 2


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

    monkeypatch.setattr(sync_to_folio_mod, "upsert_from_payloads", fake_upsert)

    run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        [_row("sel", SELECTED)],
        FakeRefCache(),  # type: ignore[arg-type]
        _FakeInventory(),
        dry_run=True,
    )

    assert isinstance(captured["ref_cache"], FakeRefCache)
