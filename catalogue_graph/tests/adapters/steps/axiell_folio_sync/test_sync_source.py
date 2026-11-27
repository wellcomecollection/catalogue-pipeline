"""Which Axiell adapter a sync run reads, and the FOLIO target that follows.

``transformer_type`` on the adapter's completed event names the source: "axiell"
(the live system) or "axiell_uat". UAT records share GUIDs with live ones, so a
UAT run must only ever write to the test environment, whatever the event or
FOLIO_TARGET ask for.
"""

from __future__ import annotations

from typing import Any

import pytest
from pydantic import ValidationError

import adapters.steps.axiell_folio_sync.axiell_adapter_read as read_mod
import adapters.steps.axiell_folio_sync.axiell_folio_sync as sync_mod
from adapters.extractors.oai_pmh.axiell.runtime import AXIELL_CONFIG, AXIELL_UAT_CONFIG
from adapters.steps.axiell_folio_sync.axiell_adapter_read import read_rows
from adapters.steps.axiell_folio_sync.axiell_folio_sync import resolve_source_target
from adapters.steps.axiell_folio_sync.models import (
    AxiellFolioSyncEvent,
    AxiellFolioSyncResponse,
    FolioTarget,
    SyncSource,
)
from adapters.steps.axiell_folio_sync.run_axiell_folio_sync import run_sync

from .test_axiell_folio_sync import _clear_okapi_env, _stub_ssm

_TARGETS: list[FolioTarget | None] = [None, "prod", "test", "dev"]


@pytest.mark.parametrize("requested", _TARGETS)
def test_a_uat_run_always_targets_test(
    monkeypatch: pytest.MonkeyPatch, requested: FolioTarget | None
) -> None:
    monkeypatch.setenv("FOLIO_TARGET", "prod")

    assert resolve_source_target("axiell_uat", requested) == "test"


@pytest.mark.parametrize("requested", ["prod", "test", "dev"])
def test_a_live_run_resolves_its_target_as_before(requested: FolioTarget) -> None:
    assert resolve_source_target("axiell", requested) == requested


def test_a_live_run_with_no_target_falls_back_to_folio_target(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("FOLIO_TARGET", "dev")

    assert resolve_source_target("axiell", None) == "dev"


def test_an_unknown_source_is_rejected() -> None:
    with pytest.raises(ValidationError):
        AxiellFolioSyncEvent.model_validate(
            {"job_id": "j", "transformer_type": "folio"}
        )


@pytest.mark.parametrize(
    ("source", "expected"),
    [("axiell", AXIELL_CONFIG), ("axiell_uat", AXIELL_UAT_CONFIG)],
)
def test_read_rows_reads_the_source_s_tables(
    monkeypatch: pytest.MonkeyPatch, source: SyncSource, expected: Any
) -> None:
    built: list[Any] = []

    class StopRead(Exception):
        pass

    def fake_build(config: Any, *a: Any, **kw: Any) -> Any:
        built.append(config)
        raise StopRead

    monkeypatch.setattr(read_mod.AxiellChangesetReader, "build", fake_build)

    with pytest.raises(StopRead):
        read_rows(["cs1"], None, use_rest_api_table=True, source=source)

    assert built == [expected]


def test_read_rows_defaults_to_the_live_adapter(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    built: list[Any] = []

    class StopRead(Exception):
        pass

    def fake_build(config: Any, *a: Any, **kw: Any) -> Any:
        built.append(config)
        raise StopRead

    monkeypatch.setattr(read_mod.AxiellChangesetReader, "build", fake_build)

    with pytest.raises(StopRead):
        read_rows(["cs1"], None, use_rest_api_table=True)

    assert built == [AXIELL_CONFIG]


@pytest.mark.parametrize(
    ("event_fields", "expected_source", "expected_url"),
    [
        ({}, "axiell", "https://folio.example.org"),
        ({"transformer_type": "axiell"}, "axiell", "https://folio.example.org"),
        # A UAT event naming prod still builds the test client.
        ({"transformer_type": "axiell_uat"}, "axiell_uat", "https://test.example"),
    ],
)
def test_handler_reads_the_source_and_builds_its_client(
    monkeypatch: pytest.MonkeyPatch,
    event_fields: dict[str, Any],
    expected_source: str,
    expected_url: str,
) -> None:
    """The wiring from the event to the table read and the FOLIO client built.
    Every event asks for prod, so the UAT case shows the source overriding it."""
    _clear_okapi_env(monkeypatch)
    monkeypatch.setenv("OKAPI_SECRET_PARAM", "/prod/okapi")
    monkeypatch.setenv("OKAPI_TEST_SECRET_PARAM", "/test/okapi")
    _stub_ssm(
        monkeypatch,
        {
            "/prod/okapi": {
                "url": "https://folio.example.org",
                "tenant": "wellcome",
                "username": "prod-user",
                "password": "prod-pw",
            },
            "/test/okapi": {
                "url": "https://test.example",
                "tenant": "wellcome-test",
                "username": "sync-user",
                "password": "test-pw",
            },
        },
    )

    built: dict[str, Any] = {}

    class FakeFolioClient:
        def __init__(self, url: str, tenant: str, **kwargs: Any) -> None:
            built["url"] = url

    class FakeRefCache:
        def __init__(self, _inventory: Any) -> None:
            pass

        def load(self) -> FakeRefCache:
            return self

    read: dict[str, Any] = {}

    def fake_read_rows(*a: Any, **kw: Any) -> Any:
        read.update(kw)
        return [], [], []

    ran: dict[str, Any] = {}

    def fake_run_sync(event: AxiellFolioSyncEvent, *a: Any, **kw: Any) -> Any:
        ran.update(kw)
        return AxiellFolioSyncResponse(
            job_id=event.job_id, dry_run=True, folio_target=kw["folio_target"]
        )

    monkeypatch.setattr(sync_mod, "FolioClient", FakeFolioClient)
    monkeypatch.setattr(sync_mod, "FolioInventoryClient", lambda client: client)
    monkeypatch.setattr(sync_mod, "ssl_context_from_env", lambda: None)
    monkeypatch.setattr(sync_mod, "RefCache", FakeRefCache)
    monkeypatch.setattr(sync_mod, "read_rows", fake_read_rows)
    monkeypatch.setattr(sync_mod, "run_sync", fake_run_sync)

    event = AxiellFolioSyncEvent(
        job_id="j", changeset_ids=["cs1"], folio_target="prod", **event_fields
    )
    sync_mod.handler(event, use_rest_api_table=False)

    assert read["source"] == expected_source
    assert built["url"] == expected_url
    assert ran["folio_target"] == (
        "test" if expected_source == "axiell_uat" else "prod"
    )


def test_live_and_uat_reports_with_the_same_job_id_do_not_collide(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Both adapters derive job_id from the trigger minute, so a live run and a
    UAT run can share one. Each must keep its own manifest."""
    written: list[str] = []
    monkeypatch.setattr(
        "utils.reporting.pydantic_to_s3_json",
        lambda model, s3_uri: written.append(s3_uri),
    )

    for source in ("axiell", "axiell_uat"):
        run_sync(
            AxiellFolioSyncEvent(
                job_id="20261127T1015", changeset_ids=[], transformer_type=source
            ),
            [],
            None,  # type: ignore[arg-type]
            None,  # type: ignore[arg-type]
            dry_run=True,
            folio_target="test",
            manifest_bucket="bucket-1",
        )

    assert written == [
        "s3://bucket-1/manifests/axiell/20261127T1015.json",
        "s3://bucket-1/manifests/axiell_uat/20261127T1015.json",
    ]


_OKAPI_OVERRIDES = ["OKAPI_URL", "OKAPI_TENANT", "OKAPI_USERNAME", "OKAPI_PASSWORD"]


def _stub_handler_dependencies(monkeypatch: pytest.MonkeyPatch) -> dict[str, Any]:
    """Stub everything past credential resolution, recording the FOLIO URL the
    handler builds its client for."""
    built: dict[str, Any] = {}

    class FakeFolioClient:
        def __init__(self, url: str, tenant: str, **kwargs: Any) -> None:
            built["url"] = url

    class FakeRefCache:
        def __init__(self, _inventory: Any) -> None:
            pass

        def load(self) -> FakeRefCache:
            return self

    def fake_run_sync(event: AxiellFolioSyncEvent, *a: Any, **kw: Any) -> Any:
        return AxiellFolioSyncResponse(
            job_id=event.job_id, dry_run=True, folio_target=kw["folio_target"]
        )

    monkeypatch.setattr(sync_mod, "FolioClient", FakeFolioClient)
    monkeypatch.setattr(sync_mod, "FolioInventoryClient", lambda client: client)
    monkeypatch.setattr(sync_mod, "ssl_context_from_env", lambda: None)
    monkeypatch.setattr(sync_mod, "RefCache", FakeRefCache)
    monkeypatch.setattr(sync_mod, "read_rows", lambda *a, **kw: ([], [], []))
    monkeypatch.setattr(sync_mod, "run_sync", fake_run_sync)
    return built


@pytest.mark.parametrize("override", _OKAPI_OVERRIDES)
def test_a_uat_run_refuses_an_unscoped_okapi_override(
    monkeypatch: pytest.MonkeyPatch, override: str
) -> None:
    """An OKAPI_* override applies to whichever target the run resolves, so a UAT
    run with one set could write UAT records with another tenant's details. It
    fails before any credentials are read or a FOLIO client is built."""
    _clear_okapi_env(monkeypatch)
    monkeypatch.setenv("OKAPI_TEST_SECRET_PARAM", "/test/okapi")
    monkeypatch.setenv(override, "from-env")
    fetched = _stub_ssm(monkeypatch, {})
    built = _stub_handler_dependencies(monkeypatch)

    with pytest.raises(ValueError, match="unscoped OKAPI credential overrides"):
        sync_mod.handler(
            AxiellFolioSyncEvent(job_id="j", transformer_type="axiell_uat"),
            use_rest_api_table=False,
        )

    assert fetched == []
    assert built == {}


def test_a_live_run_still_honours_okapi_overrides(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """The check is UAT only: a live run (here a prod dry run) keeps using the
    overrides, as local runs rely on them."""
    _clear_okapi_env(monkeypatch)
    for name in _OKAPI_OVERRIDES:
        monkeypatch.setenv(
            name, "https://override.example" if name == "OKAPI_URL" else "x"
        )
    built = _stub_handler_dependencies(monkeypatch)

    response = sync_mod.handler(
        AxiellFolioSyncEvent(
            job_id="j", transformer_type="axiell", folio_target="prod"
        ),
        use_rest_api_table=False,
    )

    assert built["url"] == "https://override.example"
    assert response.folio_target == "prod"
