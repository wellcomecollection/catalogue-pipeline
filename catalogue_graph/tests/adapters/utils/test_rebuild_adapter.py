from collections.abc import Iterable
from datetime import UTC, datetime, timedelta
from pathlib import Path
from types import SimpleNamespace
from typing import cast

import httpx
import pyarrow.parquet as pq
import pytest
from lxml import etree
from oai_pmh_client.client import OAIClient
from pyiceberg.table import Table as IcebergTable

import scripts.rebuild_adapter as rebuild_adapter
from adapters.extractors.oai_pmh.folio.enrichment.inventory_client import (
    FolioInventoryClient,
)
from adapters.extractors.oai_pmh.runtime import OAIPMHAdapterConfig
from adapters.steps.oai_pmh.reconcile import ReconcileRuntime
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore
from adapters.utils.window_store import WindowStore
from adapters.utils.window_summary import WindowSummary
from tests.adapters.conftest import (
    adapter_records_to_table,
)


def test_populate_store_from_snapshot(
    temporary_table: IcebergTable, tmp_path: Path
) -> None:
    store = AdapterStore(temporary_table, "test_namespace")
    snapshot_path = tmp_path / "snapshot.parquet"
    pq.write_table(
        adapter_records_to_table(
            [
                {"id": "rec001", "content": "hello"},
                {"id": "rec002", "content": "world"},
            ]
        ),
        snapshot_path,
    )

    changeset_ids = rebuild_adapter._populate_store_from_snapshot(
        store, str(snapshot_path)
    )

    assert len(changeset_ids) == 1
    assert store.get_all_records().num_rows == 2


def test_download_items_to_snapshot_aborts_on_zero_items(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    bib_snapshot_path = tmp_path / "bibs.parquet"
    pq.write_table(
        adapter_records_to_table([{"id": "oai:folio:set/uuid-1", "content": "x"}]),
        bib_snapshot_path,
    )
    monkeypatch.setattr(
        rebuild_adapter, "fetch_item_rows", lambda *args, **kwargs: None
    )
    items_snapshot_path = tmp_path / "items.parquet"

    with pytest.raises(RuntimeError, match="0 items"):
        rebuild_adapter._download_items_to_snapshot(
            cast(FolioInventoryClient, SimpleNamespace()),
            bib_snapshot_path=str(bib_snapshot_path),
            items_snapshot_path=str(items_snapshot_path),
            namespace="test_namespace",
        )

    assert not items_snapshot_path.exists()
    assert not (tmp_path / "items.parquet.partial").exists()


def test_rebuild_refuses_local_tables_with_publish() -> None:
    with pytest.raises(ValueError, match="skip-publish-event"):
        rebuild_adapter.rebuild_adapter(
            "axiell", use_rest_api_table=False, snapshot_path="/nonexistent"
        )


def test_download_client_is_tuned_for_a_long_download(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """The download must not inherit the adapter's windowed-harvest settings,
    which allow no retries and a 60 second read."""
    built_with: list[dict] = []
    adapter_timeout = httpx.Timeout(connect=10.0, read=60.0, write=11.0, pool=12.0)

    class _StopAfterClientBuild(Exception):
        pass

    config_stub = SimpleNamespace(
        build_http_client=lambda: httpx.Client(timeout=adapter_timeout),
        build_oai_client=lambda **kwargs: built_with.append(kwargs),
        config=SimpleNamespace(),
    )
    monkeypatch.setattr(rebuild_adapter, "get_config", lambda adapter_type: config_stub)
    monkeypatch.setattr("builtins.input", lambda *args: "CONFIRM")
    monkeypatch.setattr(rebuild_adapter, "_wipe_window_store", lambda *a, **k: None)
    monkeypatch.setattr(rebuild_adapter, "_reset_window_cursor", lambda *a, **k: None)

    def stop(*args: object, **kwargs: object) -> None:
        raise _StopAfterClientBuild

    monkeypatch.setattr(rebuild_adapter, "_download_to_snapshot", stop)

    with pytest.raises(_StopAfterClientBuild):
        rebuild_adapter.rebuild_adapter(
            "axiell",
            use_rest_api_table=True,
            snapshot_path=str(tmp_path / "not-yet-downloaded.parquet"),
        )

    assert len(built_with) == 1
    call = built_with[0]
    assert call["max_request_retries"] == rebuild_adapter.DOWNLOAD_MAX_REQUEST_RETRIES

    timeout = call["http_client"].timeout
    assert timeout.read == rebuild_adapter.DOWNLOAD_READ_TIMEOUT_SECONDS
    assert timeout.read > adapter_timeout.read
    # Widening the read must leave the adapter's other deadlines alone.
    assert (timeout.connect, timeout.write, timeout.pool) == (10.0, 11.0, 12.0)
    call["http_client"].close()


def _list_records_xml(identifiers: list[str], token: str | None) -> etree._Element:
    """A ListRecords response in the shape the OAI-PMH client parses."""
    records = "".join(
        f"""<record><header><identifier>{identifier}</identifier>
        <datestamp>2026-07-24T00:00:00Z</datestamp></header>
        <metadata/></record>"""
        for identifier in identifiers
    )
    resumption = (
        f'<resumptionToken completeListSize="99" cursor="0">{token}</resumptionToken>'
        if token
        else "<resumptionToken/>"
    )
    return etree.fromstring(
        '<OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">'
        f"<ListRecords>{records}{resumption}</ListRecords></OAI-PMH>"
    )


def test_iter_record_pages_follows_resumption_tokens() -> None:
    """The page iterator drives the client's request loop directly, so it has
    to keep matching the client's paging behaviour."""
    requests: list[dict] = []

    def fake_request(verb: str, **params: object) -> etree._Element:
        requests.append({"verb": verb, **params})
        if "resumptionToken" in params:
            return _list_records_xml(["b"], None)
        return _list_records_xml(["a"], "token-1")

    oai_client = cast(OAIClient, SimpleNamespace(_request=fake_request))
    config = cast(
        OAIPMHAdapterConfig,
        SimpleNamespace(oai_metadata_prefix="marc21", oai_set_spec="collect"),
    )

    pages = list(rebuild_adapter._iter_record_pages(oai_client, config))

    assert [r.header.identifier for records, _ in pages for r in records] == ["a", "b"]
    assert [size for _, size in pages] == [99, None]
    # The first request carries the query, and the token request must not
    # repeat it.
    assert requests[0] == {
        "verb": "ListRecords",
        "metadataPrefix": "marc21",
        "set": "collect",
    }
    assert requests[1] == {"verb": "ListRecords", "resumptionToken": "token-1"}


def _row(identifier: str, namespace: str = "test_namespace") -> dict:
    """An adapter store row with the required fields filled in."""
    return {
        "id": identifier,
        "namespace": namespace,
        "content": identifier,
        "last_modified": datetime.now(UTC),
        "deleted": None,
    }


def _fake_record(identifier: str) -> SimpleNamespace:
    return SimpleNamespace(header=SimpleNamespace(identifier=identifier))


def _download_stubs(
    pages: Iterable[tuple[list, int | None]], monkeypatch: pytest.MonkeyPatch
) -> tuple[OAIClient, OAIPMHAdapterConfig]:
    monkeypatch.setattr(
        rebuild_adapter,
        "build_adapter_store_row",
        lambda namespace, identifier, record: _row(identifier, namespace),
    )
    monkeypatch.setattr(
        rebuild_adapter, "_iter_record_pages", lambda *args, **kwargs: iter(pages)
    )
    oai_client = cast(OAIClient, SimpleNamespace())
    config = cast(
        OAIPMHAdapterConfig,
        SimpleNamespace(
            oai_metadata_prefix="marc21",
            oai_set_spec=None,
            adapter_namespace="test_namespace",
        ),
    )
    return oai_client, config


def test_download_to_snapshot_aborts_on_zero_records(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    oai_client, config = _download_stubs([([], None)], monkeypatch)
    snapshot_path = tmp_path / "snapshot.parquet"

    with pytest.raises(RuntimeError, match="returned 0 records"):
        rebuild_adapter._download_to_snapshot(oai_client, config, str(snapshot_path))

    assert not snapshot_path.exists()
    assert not (tmp_path / "snapshot.parquet.partial").exists()


def test_download_to_snapshot_leaves_no_snapshot_on_failure(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """An interrupted download must not leave a file at the snapshot path,
    otherwise a resumed run would silently rebuild from incomplete data."""

    def fail_mid_download() -> Iterable[tuple[list, int | None]]:
        yield [_fake_record("a")], 2
        raise ConnectionError("mid-download failure")

    oai_client, config = _download_stubs(fail_mid_download(), monkeypatch)
    snapshot_path = tmp_path / "snapshot.parquet"

    with pytest.raises(ConnectionError):
        rebuild_adapter._download_to_snapshot(oai_client, config, str(snapshot_path))

    assert not snapshot_path.exists()


def test_rebuild_adapter_orchestration_axiell(
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """End-to-end resume-path run: the store is wiped and rebuilt from the
    snapshot only, reconcile runs over the load changesets, and every load
    changeset is published."""
    adapter_store = AdapterStore(temporary_table, "test_namespace")
    temporary_table.append(
        adapter_records_to_table(
            [
                {"id": "kept", "content": "kept content"},
                {"id": "stale", "content": "stale content"},
            ]
        )
    )
    window_store = WindowStore(temporary_window_status_table)
    now = datetime.now(UTC)
    window_store.upsert(
        WindowSummary(
            window_start=now - timedelta(minutes=1),
            window_end=now,
            state="success",
            attempts=1,
            record_ids=[],
            last_error=None,
            updated_at=now,
            tags={"published_at": now.isoformat()},
        )
    )
    reconcile_runtime = ReconcileRuntime(
        adapter_store=adapter_store,
        reconciler_store=ReconcilerStore(reconciler_temporary_table, "test_namespace"),
        facts_store=DeletionFactsStore(
            deletion_facts_temporary_table, "test_namespace"
        ),
        adapter_name="axiell",
        namespace="test_namespace",
    )

    snapshot_path = tmp_path / "snapshot.parquet"
    pq.write_table(
        adapter_records_to_table(
            [
                {"id": "kept", "content": "kept content v2"},
                {"id": "new", "content": "new content"},
            ]
        ),
        snapshot_path,
    )

    config_stub = SimpleNamespace(
        build_adapter_store=lambda **kwargs: adapter_store,
        build_window_store=lambda **kwargs: window_store,
    )
    monkeypatch.setattr(rebuild_adapter, "get_config", lambda adapter_type: config_stub)
    monkeypatch.setattr(
        rebuild_adapter, "build_reconcile_runtime", lambda *a, **k: reconcile_runtime
    )
    monkeypatch.setattr("builtins.input", lambda *args: "CONFIRM")
    published: list[list[str]] = []
    monkeypatch.setattr(
        rebuild_adapter,
        "_publish_adapter_event",
        lambda adapter_type, job_id, changeset_ids: published.append(changeset_ids),
    )
    reconciled: list[list[str]] = []
    original_run_reconcile = rebuild_adapter._run_reconcile

    def recording_run_reconcile(
        runtime: ReconcileRuntime,
        adapter_type: str,
        job_id: str,
        changeset_ids: list[str],
    ) -> None:
        reconciled.append(changeset_ids)
        original_run_reconcile(runtime, adapter_type, job_id, changeset_ids)

    monkeypatch.setattr(rebuild_adapter, "_run_reconcile", recording_run_reconcile)

    rebuild_adapter.rebuild_adapter(
        "axiell", use_rest_api_table=True, snapshot_path=str(snapshot_path)
    )

    rows = sorted(
        (row["id"], row["content"])
        for row in adapter_store.get_all_records().to_pylist()
    )
    assert rows == [("kept", "kept content v2"), ("new", "new content")]

    assert len(reconciled) == 1
    assert published == [[changeset_id] for changeset_id in reconciled[0]]


def test_reconcile_runtime_sees_the_loaded_records(
    temporary_table: IcebergTable,
    temporary_window_status_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Reconcile must see the loaded rows, so its runtime is built after the load.

    A pyiceberg handle is pinned to the snapshot it opened at, so a runtime built
    before the load reads nothing. Give build_reconcile_runtime a handle opened
    at call time, as production does, and assert reconcile sees every record.
    """
    adapter_store = AdapterStore(temporary_table, "test_namespace")
    window_store = WindowStore(temporary_window_status_table)

    snapshot_path = tmp_path / "snapshot.parquet"
    pq.write_table(
        adapter_records_to_table(
            [{"id": f"rec{i}", "content": f"content {i}"} for i in range(3)]
        ),
        snapshot_path,
    )

    def build_runtime_with_fresh_handle(
        adapter_type: str, *, use_rest_api_table: bool
    ) -> ReconcileRuntime:
        # Reopen from the catalog like production's build_adapter_store, so the
        # handle captures the table state at the moment the runtime is built.
        fresh = temporary_table.catalog.load_table(temporary_table.name())
        return ReconcileRuntime(
            adapter_store=AdapterStore(fresh, "test_namespace"),
            reconciler_store=ReconcilerStore(
                reconciler_temporary_table, "test_namespace"
            ),
            facts_store=DeletionFactsStore(
                deletion_facts_temporary_table, "test_namespace"
            ),
            adapter_name="axiell",
            namespace="test_namespace",
        )

    config_stub = SimpleNamespace(
        build_adapter_store=lambda **kwargs: adapter_store,
        build_window_store=lambda **kwargs: window_store,
    )
    monkeypatch.setattr(rebuild_adapter, "get_config", lambda adapter_type: config_stub)
    monkeypatch.setattr(
        rebuild_adapter, "build_reconcile_runtime", build_runtime_with_fresh_handle
    )
    monkeypatch.setattr("builtins.input", lambda *args: "CONFIRM")

    rows_seen_by_reconcile: list[int] = []
    original_run_reconcile = rebuild_adapter._run_reconcile

    def recording_run_reconcile(
        runtime: ReconcileRuntime,
        adapter_type: str,
        job_id: str,
        changeset_ids: list[str],
    ) -> None:
        rows_seen_by_reconcile.append(
            runtime.adapter_store.get_records_by_changesets(changeset_ids).num_rows
        )
        original_run_reconcile(runtime, adapter_type, job_id, changeset_ids)

    monkeypatch.setattr(rebuild_adapter, "_run_reconcile", recording_run_reconcile)

    rebuild_adapter.rebuild_adapter(
        "axiell",
        use_rest_api_table=True,
        snapshot_path=str(snapshot_path),
        skip_publish_event=True,
    )

    # Every loaded record is visible to reconcile; 0 would mean a stale handle.
    assert rows_seen_by_reconcile == [3]
