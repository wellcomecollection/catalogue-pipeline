"""Handler-level tests for the Axiell to Folio sync step.

These exercise how run_sync selects, skips, and processes rows using injected
fakes (a stub ref cache + FOLIO callables) rather than patching module globals.
"""

from __future__ import annotations

import argparse
import json
from collections.abc import Mapping
from typing import Any, get_args

import pytest
from pydantic import ValidationError

import adapters.steps.axiell_folio_sync.axiell_folio_sync as sync_mod
import adapters.steps.axiell_folio_sync.folio.okapi as okapi_mod
import adapters.steps.axiell_folio_sync.run_axiell_folio_sync as run_sync_mod
from adapters.steps.axiell_folio_sync.folio.okapi import (
    load_okapi_config,
    resolve_folio_target,
)
from adapters.steps.axiell_folio_sync.models import (
    AxiellFolioSyncEvent,
    AxiellFolioSyncResponse,
    FolioTarget,
)
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


OKAPI_ENV_VARS = (
    "OKAPI_URL",
    "OKAPI_TENANT",
    "OKAPI_USERNAME",
    "OKAPI_PASSWORD",
    "OKAPI_SECRET_PARAM",
    "OKAPI_DEV_SECRET_PARAM",
    "FOLIO_TARGET",
)


def _clear_okapi_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for key in OKAPI_ENV_VARS:
        monkeypatch.delenv(key, raising=False)


def _stub_ssm(monkeypatch: pytest.MonkeyPatch, params: Mapping[str, Any]) -> list[str]:
    """Serve the given SSM parameter values, recording which names were fetched."""
    fetched: list[str] = []

    class FakeSsm:
        def get_parameter(self, Name: str, WithDecryption: bool) -> dict[str, Any]:  # noqa: N803
            fetched.append(Name)
            return {"Parameter": {"Value": json.dumps(params[Name])}}

    monkeypatch.setattr(okapi_mod, "_ssm", lambda: FakeSsm())
    return fetched


def test_load_okapi_config_raises_clear_error_for_missing_fields(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _clear_okapi_env(monkeypatch)

    with pytest.raises(ValueError, match="Missing OKAPI configuration fields"):
        load_okapi_config()


def test_load_okapi_config_dev_target_reads_the_dev_parameter(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _clear_okapi_env(monkeypatch)
    monkeypatch.setenv("OKAPI_SECRET_PARAM", "/prod/okapi")
    monkeypatch.setenv("OKAPI_DEV_SECRET_PARAM", "/dev/okapi")
    fetched = _stub_ssm(
        monkeypatch,
        {
            "/prod/okapi": {
                "url": "https://folio.example.org",
                "tenant": "wellcome",
                "username": "prod-user",
                "password": "prod-pw",
            },
            "/dev/okapi": {
                "url": "http://sandbox.internal:8000",
                "tenant": "diku",
                "username": "diku_admin",
                "password": "dev-pw",
            },
        },
    )

    config = load_okapi_config("dev")

    assert config["url"] == "http://sandbox.internal:8000"
    assert config["tenant"] == "diku"
    # The prod parameter is never even read on a dev-targeted run.
    assert fetched == ["/dev/okapi"]


def test_load_okapi_config_dev_target_does_not_fall_back_to_prod(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Dev target with no dev parameter configured must fail rather than quietly
    # writing to the production tenant.
    _clear_okapi_env(monkeypatch)
    monkeypatch.setenv("OKAPI_SECRET_PARAM", "/prod/okapi")

    with pytest.raises(ValueError, match="OKAPI_DEV_SECRET_PARAM"):
        load_okapi_config("dev")


def test_folio_target_defaults_to_prod_and_env_var_is_a_fallback(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _clear_okapi_env(monkeypatch)
    assert resolve_folio_target() == "prod"
    assert resolve_folio_target("dev") == "dev"

    monkeypatch.setenv("FOLIO_TARGET", "dev")
    assert resolve_folio_target() == "dev"
    # An explicit target still wins over the env var.
    assert resolve_folio_target("prod") == "prod"


def test_unknown_folio_target_is_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_okapi_env(monkeypatch)

    with pytest.raises(ValueError, match="Unknown FOLIO target 'staging'"):
        resolve_folio_target("staging")


def test_every_folio_target_has_a_secret_parameter() -> None:
    """The registry and the FolioTarget type must stay in step.

    Typing SECRET_PARAM_ENV_VARS as dict[FolioTarget, str] makes a key that is not
    a valid target a type error, but not the reverse: widening FolioTarget without
    adding to the registry only fails at runtime, on a run against the new target.
    """
    assert set(okapi_mod.SECRET_PARAM_ENV_VARS) == set(get_args(FolioTarget))
    assert okapi_mod.DEFAULT_FOLIO_TARGET in okapi_mod.SECRET_PARAM_ENV_VARS


@pytest.mark.parametrize("given", ["DEV", " dev ", "Dev", "\tdev\n"])
def test_folio_target_is_normalised(
    monkeypatch: pytest.MonkeyPatch, given: str
) -> None:
    # Accepted from an env var or a hand-written event without exact casing.
    _clear_okapi_env(monkeypatch)
    assert resolve_folio_target(given) == "dev"

    monkeypatch.setenv("FOLIO_TARGET", given)
    assert resolve_folio_target() == "dev"


@pytest.mark.parametrize("blank", ["", "   "])
def test_blank_folio_target_falls_back_to_prod(
    monkeypatch: pytest.MonkeyPatch, blank: str
) -> None:
    # An empty env var is treated as unset rather than as an unknown target.
    _clear_okapi_env(monkeypatch)
    monkeypatch.setenv("FOLIO_TARGET", blank)
    assert resolve_folio_target() == "prod"
    assert resolve_folio_target(blank) == "prod"


def test_event_rejects_an_unknown_folio_target() -> None:
    with pytest.raises(ValidationError):
        AxiellFolioSyncEvent.model_validate({"job_id": "j1", "folio_target": "staging"})


@pytest.mark.parametrize(
    ("folio_target", "expected_url", "expected_param"),
    [
        ("prod", "https://folio.example.org", "/prod/okapi"),
        ("dev", "http://sandbox.internal:8000", "/dev/okapi"),
    ],
)
def test_handler_builds_the_client_for_the_target_on_the_event(
    monkeypatch: pytest.MonkeyPatch,
    folio_target: str,
    expected_url: str,
    expected_param: str,
) -> None:
    """The event's folio_target must reach the FOLIO client that gets built.

    resolve_folio_target and load_okapi_config are covered directly elsewhere.
    This covers the wiring between them and an actual event, which is where a
    dropped folio_target silently sends a dev-targeted run to production.
    """
    _clear_okapi_env(monkeypatch)
    monkeypatch.setenv("OKAPI_SECRET_PARAM", "/prod/okapi")
    monkeypatch.setenv("OKAPI_DEV_SECRET_PARAM", "/dev/okapi")
    fetched = _stub_ssm(
        monkeypatch,
        {
            "/prod/okapi": {
                "url": "https://folio.example.org",
                "tenant": "wellcome",
                "username": "prod-user",
                "password": "prod-pw",
            },
            "/dev/okapi": {
                "url": "http://sandbox.internal:8000",
                "tenant": "diku",
                "username": "diku_admin",
                "password": "dev-pw",
            },
        },
    )

    built: dict[str, Any] = {}

    class FakeFolioClient:
        def __init__(self, url: str, tenant: str, **kwargs: Any) -> None:
            built["url"] = url
            built["tenant"] = tenant
            built["username"] = kwargs.get("username")

    class FakeRefCache:
        def __init__(self, _inventory: Any) -> None:
            pass

        def load(self) -> FakeRefCache:
            return self

    monkeypatch.setattr(sync_mod, "FolioClient", FakeFolioClient)
    monkeypatch.setattr(sync_mod, "FolioInventoryClient", lambda client: client)
    monkeypatch.setattr(sync_mod, "ssl_context_from_env", lambda: None)
    monkeypatch.setattr(sync_mod, "RefCache", FakeRefCache)
    monkeypatch.setattr(sync_mod, "read_rows", lambda *a, **kw: ([], []))
    monkeypatch.setattr(
        sync_mod,
        "run_sync",
        lambda event, *a, **kw: AxiellFolioSyncResponse(
            job_id=event.job_id, dry_run=True
        ),
    )

    event = AxiellFolioSyncEvent(job_id="j1", folio_target=folio_target)
    sync_mod.handler(event, use_rest_api_table=False)

    assert built["url"] == expected_url
    # Only the parameter for the requested target is read, so a run can never
    # pick up the other instance's credentials.
    assert fetched == [expected_param]


def _run_local_handler(
    monkeypatch: pytest.MonkeyPatch, argv: list[str]
) -> AxiellFolioSyncEvent:
    """Drive local_handler with the given CLI args, returning the event it built."""
    captured: dict[str, AxiellFolioSyncEvent] = {}

    def fake_handler(event: AxiellFolioSyncEvent, **kwargs: Any) -> Any:
        captured["event"] = event
        return AxiellFolioSyncResponse(job_id=event.job_id, dry_run=True)

    monkeypatch.setattr(sync_mod, "handler", fake_handler)
    monkeypatch.setattr("sys.argv", ["axiell_folio_sync", *argv])
    sync_mod.local_handler(argparse.ArgumentParser())
    return captured["event"]


def test_cli_folio_target_flag_reaches_the_event(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    event = _run_local_handler(monkeypatch, ["--job-id", "j1", "--folio-target", "dev"])

    assert event.folio_target == "dev"
    # Dry-run unless --live is passed, so a dev smoke test cannot write by default.
    assert event.dry_run is True
    capsys.readouterr()


def test_cli_folio_target_defaults_to_none_so_the_env_var_applies(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    # None rather than "prod": the CLI must not override FOLIO_TARGET by default.
    event = _run_local_handler(monkeypatch, ["--job-id", "j1"])

    assert event.folio_target is None
    capsys.readouterr()


def test_cli_rejects_an_unknown_folio_target(monkeypatch: pytest.MonkeyPatch) -> None:
    with pytest.raises(SystemExit):
        _run_local_handler(monkeypatch, ["--job-id", "j1", "--folio-target", "staging"])


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

    monkeypatch.setattr(run_sync_mod, "upsert_from_payloads", failing_upsert)

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

    monkeypatch.setattr(run_sync_mod, "upsert_from_payloads", fake_upsert)

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


def test_object_number_extracts_the_altrefno_035_stripped() -> None:
    # A record carries several 035$a subfields, one per identifier scheme (all
    # prefixed by the XSLT); object_number must be the (AltRefNo) one, not the first
    # 035$a (Adlib serialisation order is arbitrary), with its prefix stripped.
    from adapters.steps.axiell_folio_sync.mapping import extract, parse_xml

    xml = (
        "<record>"
        "<controlfield tag='001'>g1</controlfield>"
        "<datafield tag='035'><subfield code='a'>(accession number)Acc123</subfield></datafield>"
        "<datafield tag='035'><subfield code='a'>(AltRefNo)SA/BSI/A/1</subfield></datafield>"
        "<datafield tag='035'><subfield code='a'>(Bibliographic Number)b12345</subfield></datafield>"
        "</record>"
    )
    assert extract(parse_xml(xml), "035$a(AltRefNo)") == "SA/BSI/A/1"


def test_object_number_absent_when_no_altrefno_035() -> None:
    # No (AltRefNo) 035$a -> object_number is absent, so an accession- or Sierra-only
    # record gets no Local identifier (rather than picking the wrong 035$a).
    from adapters.steps.axiell_folio_sync.mapping import extract, parse_xml

    xml = (
        "<record>"
        "<controlfield tag='001'>g1</controlfield>"
        "<datafield tag='035'><subfield code='a'>(accession number)Acc123</subfield></datafield>"
        "<datafield tag='035'><subfield code='a'>(Bibliographic Number)b12345</subfield></datafield>"
        "</record>"
    )
    assert extract(parse_xml(xml), "035$a(AltRefNo)") is None


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
