"""Tests for the reconcile step's heal of 982 links left stale by a parent
renumber. All records are synthetic."""

from __future__ import annotations

from datetime import UTC, datetime
from xml.sax.saxutils import escape

import pyarrow as pa
import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.steps.oai_pmh import part_of_heal
from adapters.steps.oai_pmh.reconcile import (
    ReconcileEvent,
    ReconcileResponse,
    ReconcileRuntime,
    handler,
)
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from tests.adapters.conftest import reconciler_records_to_table

BASELINE_TIME = datetime(2026, 7, 1, 12, 0, tzinfo=UTC)
UPDATE_TIME = datetime(2026, 7, 2, 12, 0, tzinfo=UTC)
LATER_TIME = datetime(2026, 7, 3, 12, 0, tzinfo=UTC)

PARENT = "collect:100001"
CHILD_1 = "collect:100002"
CHILD_2 = "collect:100003"
UNRELATED = "collect:100004"


def _marc(
    priref: str,
    object_number: str | None,
    *,
    parent: tuple[str, str] | None = None,
    guid: str | None = None,
    title: str = "Test record",
    stamp: str = "20260701120000.0",
) -> str:
    fields = [
        f'<controlfield tag="001">{guid or f"guid-{priref}"}</controlfield>',
        f'<controlfield tag="005">{stamp}</controlfield>',
    ]
    if object_number is not None:
        fields.append(
            '<datafield tag="035" ind1=" " ind2=" ">'
            f'<subfield code="a">(AltRefNo){escape(object_number)}</subfield>'
            "</datafield>"
        )
    fields.append(
        '<datafield tag="245" ind1="0" ind2="0">'
        f'<subfield code="a">{escape(title)}</subfield></datafield>'
    )
    if parent is not None:
        fields.append(
            '<datafield tag="982" ind1=" " ind2=" ">'
            f'<subfield code="a">{parent[0]}</subfield>'
            f'<subfield code="b">{escape(parent[1])}</subfield></datafield>'
        )
    return (
        '<record xmlns="http://www.loc.gov/MARC21/slim">'
        "<leader>00000npc a2200000   4500</leader>" + "".join(fields) + "</record>"
    )


def _parent(
    object_number: str,
    *,
    guid: str | None = None,
    title: str = "Test record",
    stamp: str = "20260701120000.0",
) -> str:
    return _marc("100001", object_number, guid=guid, title=title, stamp=stamp)


def _child(priref: str, parent_number: str) -> str:
    return _marc(priref, f"{parent_number}/{priref}", parent=("100001", parent_number))


@pytest.fixture
def runtime(
    temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
) -> ReconcileRuntime:
    return ReconcileRuntime(
        adapter_store=AdapterStore(temporary_table, namespace="axiell"),
        reconciler_store=ReconcilerStore(
            reconciler_temporary_table, namespace="axiell"
        ),
        facts_store=DeletionFactsStore(
            deletion_facts_temporary_table, namespace="axiell"
        ),
        adapter_name="axiell",
        namespace="axiell",
    )


def _load(
    runtime: ReconcileRuntime,
    contents: dict[str, str | None],
    last_modified: datetime,
) -> str:
    rows = [
        {
            "namespace": "axiell",
            "id": record_id,
            "content": content,
            "changeset": None,
            "last_modified": last_modified,
            "deleted": False,
        }
        for record_id, content in contents.items()
    ]
    update = runtime.adapter_store.incremental_update(
        pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)
    )
    assert update is not None
    return update.changeset_id


def _run(runtime: ReconcileRuntime, changeset_ids: list[str]) -> ReconcileResponse:
    return handler(
        ReconcileEvent(
            job_id="test-job-id", adapter_type="axiell", changeset_ids=changeset_ids
        ),
        runtime=runtime,
    )


def _rows(runtime: ReconcileRuntime) -> dict[str, dict]:
    return {
        row["id"]: row
        for row in runtime.adapter_store.get_namespace_records().to_pylist()
    }


def _seed_family(runtime: ReconcileRuntime, parent_number: str = "TEST/1") -> str:
    return _load(
        runtime,
        {
            PARENT: _parent(parent_number),
            CHILD_1: _child("100002", parent_number),
            CHILD_2: _child("100003", parent_number),
            UNRELATED: _marc("100004", "OTHER/1", parent=("100009", "OTHER")),
        },
        BASELINE_TIME,
    )


def test_renumbered_parent_heals_its_children(runtime: ReconcileRuntime) -> None:
    baseline_changeset = _seed_family(runtime)
    before = _rows(runtime)
    changeset_id = _load(
        runtime, {PARENT: _parent("TEST/2", stamp="20260702120000.0")}, UPDATE_TIME
    )

    response = _run(runtime, [changeset_id])

    assert response.part_of_healed == 2
    after = _rows(runtime)
    for child_id in (CHILD_1, CHILD_2):
        old = before[child_id]
        new = after[child_id]
        # Only $b changes: 005 and every other byte are preserved.
        assert new["content"] == old["content"].replace(
            '<subfield code="b">TEST/1</subfield>',
            '<subfield code="b">TEST/2</subfield>',
        )
        assert '<controlfield tag="005">20260701120000.0<' in new["content"]
        assert new["last_modified"] == old["last_modified"] == BASELINE_TIME
        assert new["changeset"] == changeset_id
    assert after[UNRELATED] == before[UNRELATED]
    assert after[UNRELATED]["changeset"] == baseline_changeset

    published = runtime.adapter_store.get_records_by_changesets([changeset_id])
    assert set(published.column("id").to_pylist()) == {PARENT, CHILD_1, CHILD_2}


def test_ampersand_in_object_number_is_compared_decoded(
    runtime: ReconcileRuntime,
) -> None:
    _seed_family(runtime, parent_number="TEST/1 & 2")
    snapshot_before = runtime.adapter_store.current_snapshot_id()
    edit = _load(
        runtime,
        {PARENT: _parent("TEST/1 & 2", title="Edited title")},
        UPDATE_TIME,
    )
    snapshot_after_edit = runtime.adapter_store.current_snapshot_id()
    assert snapshot_after_edit != snapshot_before

    assert _run(runtime, [edit]).part_of_healed == 0
    assert runtime.adapter_store.current_snapshot_id() == snapshot_after_edit

    renumber = _load(runtime, {PARENT: _parent("TEST/3 & 4")}, LATER_TIME)
    assert _run(runtime, [renumber]).part_of_healed == 2
    content = _rows(runtime)[CHILD_1]["content"]
    assert '<subfield code="b">TEST/3 &amp; 4</subfield>' in content


@pytest.mark.parametrize(
    "parent_content",
    [
        pytest.param(_marc("100005", "TEST/9"), id="parent-with-no-children"),
        pytest.param(None, id="edit-with-no-renumber"),
    ],
)
def test_nothing_stale_makes_no_commit(
    runtime: ReconcileRuntime, parent_content: str | None
) -> None:
    _seed_family(runtime)
    if parent_content is None:
        edit = _load(
            runtime, {PARENT: _parent("TEST/1", title="Edited title")}, UPDATE_TIME
        )
    else:
        edit = _load(runtime, {"collect:100005": parent_content}, UPDATE_TIME)
    snapshot_id = runtime.adapter_store.current_snapshot_id()

    response = _run(runtime, [edit])

    assert response.part_of_healed == 0
    assert runtime.adapter_store.current_snapshot_id() == snapshot_id


def test_guid_changed_parent_skips_children_also_on_rerun(
    runtime: ReconcileRuntime, caplog: pytest.LogCaptureFixture
) -> None:
    _seed_family(runtime)
    runtime.reconciler_store.incremental_update(
        reconciler_records_to_table(
            [{"id": PARENT, "guid": "guid-100001", "last_modified": BASELINE_TIME}],
            namespace="axiell",
        )
    )
    before = _rows(runtime)
    changeset_id = _load(
        runtime, {PARENT: _parent("TEST/2", guid="guid-reused")}, UPDATE_TIME
    )

    response = _run(runtime, [changeset_id])

    assert response.facts_written == 1
    assert response.part_of_healed == 0
    assert '"stale_children": 2' in caplog.text

    # The mappings commit has landed, so only the facts store remembers it.
    caplog.clear()
    rerun = _run(runtime, [changeset_id])
    assert rerun.mappings_updated == 0
    assert rerun.part_of_healed == 0
    assert '"stale_children": 2' in caplog.text
    after = _rows(runtime)
    assert after[CHILD_1] == before[CHILD_1]
    assert after[CHILD_2] == before[CHILD_2]


def test_unparseable_content_is_skipped(runtime: ReconcileRuntime) -> None:
    _seed_family(runtime)
    # Passes the prefilter on the parent's priref but doesn't parse.
    _load(
        runtime,
        {"collect:100006": '<record><subfield code="a">100001</subfield>'},
        BASELINE_TIME,
    )
    changeset_id = _load(
        runtime,
        {
            PARENT: _parent("TEST/2"),
            "collect:100007": "<record><broken",
            "collect:100008": None,
        },
        UPDATE_TIME,
    )

    response = _run(runtime, [changeset_id])

    assert response.skipped == 2
    assert response.part_of_healed == 2


def test_parent_and_child_in_the_same_changeset(runtime: ReconcileRuntime) -> None:
    _seed_family(runtime)
    changeset_id = _load(
        runtime,
        {
            PARENT: _parent("TEST/2"),
            # Re-exported with the new number already, so nothing to heal.
            CHILD_1: _marc(
                "100002", "TEST/2/100002", parent=("100001", "TEST/2"), title="New"
            ),
        },
        UPDATE_TIME,
    )
    child_1_content = _rows(runtime)[CHILD_1]["content"]

    response = _run(runtime, [changeset_id])

    assert response.part_of_healed == 1
    after = _rows(runtime)
    assert after[CHILD_1]["content"] == child_1_content
    assert '<subfield code="b">TEST/2</subfield>' in after[CHILD_2]["content"]
    assert after[CHILD_1]["changeset"] == after[CHILD_2]["changeset"] == changeset_id


def test_children_are_tagged_with_their_own_parents_changeset(
    runtime: ReconcileRuntime,
) -> None:
    _seed_family(runtime)
    second_parent = "collect:100010"
    second_child = "collect:100011"
    _load(
        runtime,
        {
            second_parent: _marc("100010", "SECOND/1"),
            second_child: _marc(
                "100011", "SECOND/1/100011", parent=("100010", "SECOND/1")
            ),
        },
        BASELINE_TIME,
    )
    first = _load(runtime, {PARENT: _parent("TEST/2")}, UPDATE_TIME)
    second = _load(runtime, {second_parent: _marc("100010", "SECOND/2")}, LATER_TIME)

    response = _run(runtime, [first, second])

    assert response.part_of_healed == 3
    after = _rows(runtime)
    assert '<subfield code="b">TEST/2</subfield>' in after[CHILD_1]["content"]
    assert '<subfield code="b">SECOND/2</subfield>' in after[second_child]["content"]
    assert after[CHILD_1]["changeset"] == after[CHILD_2]["changeset"] == first
    assert after[second_child]["changeset"] == second

    store = runtime.adapter_store
    assert set(store.get_records_by_changesets([first]).column("id").to_pylist()) == {
        PARENT,
        CHILD_1,
        CHILD_2,
    }
    assert set(store.get_records_by_changesets([second]).column("id").to_pylist()) == {
        second_parent,
        second_child,
    }


def test_rerun_after_heal_is_a_no_op(runtime: ReconcileRuntime) -> None:
    _seed_family(runtime)
    changeset_id = _load(runtime, {PARENT: _parent("TEST/2")}, UPDATE_TIME)
    assert _run(runtime, [changeset_id]).part_of_healed == 2
    snapshot_id = runtime.adapter_store.current_snapshot_id()

    assert _run(runtime, [changeset_id]).part_of_healed == 0
    assert runtime.adapter_store.current_snapshot_id() == snapshot_id


def test_child_changed_after_the_scan_is_left_alone(
    runtime: ReconcileRuntime, monkeypatch: pytest.MonkeyPatch
) -> None:
    _seed_family(runtime)
    changeset_id = _load(runtime, {PARENT: _parent("TEST/2")}, UPDATE_TIME)
    newer_child = _marc("100002", "TEST/2/100002", parent=("100001", "TEST/2"))
    table = runtime.adapter_store.table
    refresh = table.refresh

    def loader_write_then_refresh() -> IcebergTable:
        monkeypatch.undo()
        _load(runtime, {CHILD_1: newer_child}, LATER_TIME)
        return refresh()

    monkeypatch.setattr(table, "refresh", loader_write_then_refresh)

    response = _run(runtime, [changeset_id])

    assert response.part_of_healed == 1
    after = _rows(runtime)
    assert after[CHILD_1]["content"] == newer_child
    assert after[CHILD_1]["last_modified"] == LATER_TIME
    assert after[CHILD_2]["changeset"] == changeset_id


def test_parent_rewritten_after_the_scan_skips_its_children(
    runtime: ReconcileRuntime, monkeypatch: pytest.MonkeyPatch
) -> None:
    _seed_family(runtime)
    changeset_id = _load(runtime, {PARENT: _parent("TEST/2")}, UPDATE_TIME)
    before = _rows(runtime)
    table = runtime.adapter_store.table
    refresh = table.refresh

    def loader_write_then_refresh() -> IcebergTable:
        monkeypatch.undo()
        _load(runtime, {PARENT: _parent("TEST/3")}, LATER_TIME)
        return refresh()

    monkeypatch.setattr(table, "refresh", loader_write_then_refresh)

    response = _run(runtime, [changeset_id])

    assert response.part_of_healed == 0
    after = _rows(runtime)
    for child in (CHILD_1, CHILD_2):
        assert after[child]["content"] == before[child]["content"]
        assert after[child]["changeset"] == before[child]["changeset"]


def test_prefilter_is_chunked_by_parent_count(
    runtime: ReconcileRuntime, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(part_of_heal, "REGEX_CHUNK_SIZE", 1)
    _seed_family(runtime)
    other_parent = _marc("100005", "TEST/5", stamp="20260702120000.0")
    changeset_id = _load(
        runtime,
        {
            PARENT: _parent("TEST/2", stamp="20260702120000.0"),
            "collect:100005": other_parent,
        },
        UPDATE_TIME,
    )

    response = _run(runtime, [changeset_id])

    assert response.part_of_healed == 2
    after = _rows(runtime)
    assert "TEST/2" in after[CHILD_1]["content"]
