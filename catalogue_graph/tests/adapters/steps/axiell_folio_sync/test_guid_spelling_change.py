"""A spelling-only change to MARC 001 must not delete the record it identifies.

The FOLIO sync derives the same record ids from any spelling of a UUID, so if a
case change to 001 produced a deliverable deletion fact, Pass 1 would update
the live records and Pass 2 would then suppress (or hard-delete) those same
records. These tests run the real reconcile step, stores and changeset reader
into ``run_sync``, with only FOLIO faked.
"""

from __future__ import annotations

from collections.abc import Mapping
from datetime import UTC, datetime
from typing import Any

import pyarrow as pa
import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.steps.axiell_folio_sync.mapping import (
    _holdings_id,
    _instance_id,
    _item_id,
)
from adapters.steps.axiell_folio_sync.models import AxiellFolioSyncEvent
from adapters.steps.axiell_folio_sync.run_axiell_folio_sync import run_sync
from adapters.steps.oai_pmh.reconcile import (
    ReconcileEvent,
    ReconcileRuntime,
    handler,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.axiell_changeset_reader import AxiellChangesetReader
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from tests.adapters.conftest import (
    deletion_facts_records_to_table,
    reconciler_records_to_table,
)

from .test_item_mapping import FakeRefCache

NAMESPACE = "axiell"
GUID = "3fe75c01-9eb1-47b4-ac50-0772aa1c11f9"
BASELINE_TIME = datetime(2026, 7, 1, 12, 0, tzinfo=UTC)
UPDATE_TIME = datetime(2026, 7, 2, 12, 0, tzinfo=UTC)

_LIST_KEYS = {
    "/inventory/instances": "instances",
    "/holdings-storage/holdings": "holdingsRecords",
    "/inventory/items": "items",
}


def _item_marcxml(guid: str) -> str:
    """A record the sync selects and maps, identified by ``guid``."""
    return (
        "<record><leader>00000nam a2200000   4500</leader>"
        f"<controlfield tag='001'>{guid}</controlfield>"
        "<datafield tag='980'><subfield code='a'>Y</subfield></datafield>"
        "<datafield tag='351'><subfield code='c'>ITEM</subfield></datafield>"
        "<datafield tag='245'><subfield code='a'>A Title</subfield></datafield>"
        "<datafield tag='655'><subfield code='a'>Archives - Non-digital</subfield>"
        "</datafield>"
        "<datafield tag='984'><subfield code='b'>215;HOME 1</subfield></datafield>"
        "</record>"
    )


class LiveFolio:
    """FOLIO already holding the record's instance, holdings and item."""

    def __init__(self, guid: str) -> None:
        self.records = {
            "/inventory/instances": {"id": _instance_id(guid), "hrid": "in1"},
            "/holdings-storage/holdings": {"id": _holdings_id(guid), "hrid": "ho1"},
            "/inventory/items": {"id": _item_id(guid), "hrid": "it1"},
        }
        self.puts: list[tuple[str, dict[str, Any]]] = []
        self.deletes: list[str] = []

    def get(self, path: str, params: Mapping[str, Any] | None = None) -> dict[str, Any]:
        record = self.records[path]
        query = (params or {}).get("query")
        found = query == f'id=="{record["id"]}"'
        return {_LIST_KEYS[path]: [record] if found else []}

    def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        raise AssertionError(f"the records exist, so nothing is created: {path}")

    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        self.puts.append((path, dict(payload)))
        return {}

    def delete(self, path: str) -> dict[str, Any]:
        self.deletes.append(path)
        return {}

    def suppressed(self) -> list[str]:
        return [path for path, body in self.puts if body.get("discoverySuppress")]


@pytest.fixture
def runtime(
    temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
) -> ReconcileRuntime:
    return ReconcileRuntime(
        adapter_store=AdapterStore(temporary_table, namespace=NAMESPACE),
        reconciler_store=ReconcilerStore(
            reconciler_temporary_table, namespace=NAMESPACE
        ),
        facts_store=DeletionFactsStore(
            deletion_facts_temporary_table, namespace=NAMESPACE
        ),
        adapter_name="axiell",
        namespace=NAMESPACE,
    )


def _respell_001(runtime: ReconcileRuntime, old: str, new: str) -> str:
    """Store a mapping under ``old``, then load the record with 001 ``new``.

    Returns the changeset the record arrived in."""
    runtime.reconciler_store.incremental_update(
        reconciler_records_to_table(
            [{"id": "collect-1", "guid": old, "last_modified": BASELINE_TIME}],
            namespace=NAMESPACE,
        )
    )
    changeset = runtime.adapter_store.incremental_update(
        pa.Table.from_pylist(
            [
                {
                    "namespace": NAMESPACE,
                    "id": "collect-1",
                    "content": _item_marcxml(new),
                    "changeset": None,
                    "last_modified": UPDATE_TIME,
                    "deleted": False,
                }
            ],
            schema=ADAPTER_STORE_ARROW_SCHEMA,
        )
    )
    assert changeset is not None
    return changeset.changeset_id


def _reconcile(runtime: ReconcileRuntime, changeset_id: str) -> None:
    handler(
        ReconcileEvent(
            job_id="job-1", adapter_type="axiell", changeset_ids=[changeset_id]
        ),
        runtime=runtime,
    )


def _sync(
    runtime: ReconcileRuntime, changeset_id: str, folio: LiveFolio, *, hard: bool
) -> Any:
    reader = AxiellChangesetReader(
        runtime.adapter_store,
        [changeset_id],
        facts_store=runtime.facts_store,
        reconciler_store=runtime.reconciler_store,
    )
    return run_sync(
        AxiellFolioSyncEvent(job_id="job-1", changeset_ids=[changeset_id]),
        list(reader.iter_records()),
        FakeRefCache(),  # type: ignore[arg-type]
        folio,
        dry_run=False,
        deletions=list(reader.iter_deletions()),
        hard_delete=hard,
    )


@pytest.mark.parametrize("hard", [False, True], ids=["suppress", "hard-delete"])
@pytest.mark.parametrize(
    ("old", "new"),
    [(GUID.upper(), GUID), (GUID, GUID.upper())],
    ids=["upper-to-lower", "lower-to-upper"],
)
def test_a_respelt_001_is_updated_and_not_removed(
    runtime: ReconcileRuntime, old: str, new: str, hard: bool
) -> None:
    changeset_id = _respell_001(runtime, old, new)
    _reconcile(runtime, changeset_id)
    folio = LiveFolio(new)

    response = _sync(runtime, changeset_id, folio, hard=hard)

    # Pass 1 updated all three records in place.
    assert response.counts["updated"] == 3
    assert response.counts["created"] == 0
    # Pass 2 had nothing to act on.
    assert response.counts["deletions"] == 0
    assert folio.suppressed() == []
    assert folio.deletes == []


@pytest.mark.parametrize("hard", [False, True], ids=["suppress", "hard-delete"])
def test_an_old_spelling_fact_already_stored_is_not_actioned(
    runtime: ReconcileRuntime, hard: bool
) -> None:
    """A fact written before the reconcile step compared by UUID survives in
    the facts store. Pass 2 must still leave the live record alone."""
    changeset_id = _respell_001(runtime, GUID.upper(), GUID)
    _reconcile(runtime, changeset_id)
    runtime.facts_store.append_facts(
        deletion_facts_records_to_table(
            [
                {
                    "record_id": "collect-1",
                    "guid": GUID.upper(),
                    "changeset": changeset_id,
                }
            ],
            namespace=NAMESPACE,
        )
    )
    folio = LiveFolio(GUID)

    response = _sync(runtime, changeset_id, folio, hard=hard)

    assert response.counts["updated"] == 3
    assert response.counts["deletions"] == 0
    assert folio.suppressed() == []
    assert folio.deletes == []


def test_a_real_guid_change_still_removes_the_old_records(
    runtime: ReconcileRuntime,
) -> None:
    """The control: moving to a different UUID is a deletion, and Pass 2 still
    suppresses the records derived from the old one."""
    other = "960bb5d0-6635-41d4-bff7-f9618338274c"
    changeset_id = _respell_001(runtime, GUID, other)
    _reconcile(runtime, changeset_id)
    folio = LiveFolio(GUID)

    response = _sync(runtime, changeset_id, folio, hard=False)

    assert response.counts["deletions"] == 1
    assert sorted(folio.suppressed()) == sorted(
        [
            f"/inventory/items/{_item_id(GUID)}",
            f"/holdings-storage/holdings/{_holdings_id(GUID)}",
            f"/inventory/instances/{_instance_id(GUID)}",
        ]
    )
