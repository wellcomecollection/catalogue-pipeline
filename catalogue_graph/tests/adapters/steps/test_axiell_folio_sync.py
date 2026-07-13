"""Handler-level tests for the Axiell → FOLIO sync step.

These exercise how ``run_sync`` selects, skips, and processes rows using injected
fakes (a stub ref cache + FOLIO callables) rather than patching module globals —
the same dependency-injection style as the ``folio_enrich`` step tests.
"""

from __future__ import annotations

from typing import Any

from adapters.steps.axiell_folio_sync import AxiellFolioSyncEvent, run_sync

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


def _folio_get(path: str, params: dict | None = None) -> dict:
    # Nothing exists yet -> every hrid lookup returns no records -> plan "create".
    return {}


def _no_write(*args: Any, **kwargs: Any) -> dict:
    raise AssertionError("dry-run must not issue FOLIO writes")


def _run(rows: list[dict[str, Any]]) -> Any:
    return run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=["cs1"]),
        rows,
        FakeRefCache(),  # type: ignore[arg-type]
        _folio_get,
        _no_write,
        _no_write,
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
    # deleted=true is recorded as an advisory signal, not suppressed/upserted
    # (RFC 090: authoritative deletes come from the reconciler, not this path).
    tombstone = {**_row("tomb", SELECTED), "deleted": True}
    resp = _run([tombstone])

    assert resp.counts["tombstone"] == 1
    assert resp.counts["suppressed"] == 0
    assert resp.counts["created"] == 0  # no upsert happened
    assert resp.total_successful == 0
    assert resp.total_errors == 0
