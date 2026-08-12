from datetime import UTC, datetime
from typing import Any

import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.axiell_changeset_reader import (
    AxiellChangesetReader,
    SupersededGuid,
)
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore
from tests.adapters.conftest import (
    adapter_records_to_table,
    deletion_facts_records_to_table,
    reconciler_records_to_table,
)

NAMESPACE = "axiell"

MARCXML = (
    "<record><leader>00000nam a2200000   4500</leader>"
    "<controlfield tag='005'>20251225123045.0</controlfield>"
    "<controlfield tag='001'>  guid-001  </controlfield>"
    "<datafield tag='245' ind1='0' ind2='0'>"
    "<subfield code='a'>A title</subfield></datafield>"
    "</record>"
)


def _reader(
    adapter_table: IcebergTable,
    changeset_ids: list[str],
    ids: list[str] | None = None,
    facts_table: IcebergTable | None = None,
    reconciler_table: IcebergTable | None = None,
) -> AxiellChangesetReader:
    return AxiellChangesetReader(
        AdapterStore(adapter_table, namespace=NAMESPACE),
        changeset_ids,
        ids=ids,
        facts_store=(
            DeletionFactsStore(facts_table, namespace=NAMESPACE)
            if facts_table is not None
            else None
        ),
        reconciler_store=(
            ReconcilerStore(reconciler_table, namespace=NAMESPACE)
            if reconciler_table is not None
            else None
        ),
    )


def _seed_adapter_rows(table: IcebergTable, rows: list[dict[str, Any]]) -> list[str]:
    store = AdapterStore(table, namespace=NAMESPACE)
    result = store.incremental_update(
        adapter_records_to_table(rows, namespace=NAMESPACE)
    )
    assert result is not None
    return [result.changeset_id]


def test_records_pass_through_unchanged_including_tombstones(
    temporary_table: IcebergTable,
) -> None:
    changeset_ids = _seed_adapter_rows(
        temporary_table,
        [
            {"id": "collect-1", "content": MARCXML},
            {"id": "collect-2", "content": MARCXML, "deleted": True},
        ],
    )

    rows = {
        row["id"]: row for row in _reader(temporary_table, changeset_ids).iter_records()
    }

    assert set(rows) == {"collect-1", "collect-2"}
    assert rows["collect-2"]["deleted"] is True
    assert rows["collect-2"]["content"] == MARCXML


def test_records_by_id_yields_only_named_records_including_tombstones(
    temporary_table: IcebergTable,
) -> None:
    # The changeset id is irrelevant to an id run; only seed the store.
    _seed_adapter_rows(
        temporary_table,
        [
            {"id": "collect-1", "content": MARCXML},
            {"id": "collect-2", "content": MARCXML, "deleted": True},
            {"id": "collect-3", "content": MARCXML},
        ],
    )

    rows = {
        row["id"]: row
        for row in _reader(
            temporary_table, [], ids=["collect-1", "collect-2"]
        ).iter_records()
    }

    assert set(rows) == {"collect-1", "collect-2"}
    assert rows["collect-2"]["deleted"] is True
    assert rows["collect-2"]["content"] == MARCXML


def test_records_by_id_tracks_unmatched_ids(temporary_table: IcebergTable) -> None:
    """An id that matches no store row is surfaced on the reader once
    iter_records has been fully consumed, so a recovery run can report it."""
    _seed_adapter_rows(temporary_table, [{"id": "collect-1", "content": MARCXML}])

    reader = _reader(temporary_table, [], ids=["collect-1", "collect-missing"])
    rows = {row["id"]: row for row in reader.iter_records()}

    assert set(rows) == {"collect-1"}
    assert reader.unmatched_ids == ["collect-missing"]


def test_deletions_are_typed_and_liveness_filtered(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
) -> None:
    fact_time = datetime(2026, 7, 1, tzinfo=UTC)
    facts_store = DeletionFactsStore(
        deletion_facts_temporary_table, namespace=NAMESPACE
    )
    facts_store.append_facts(
        deletion_facts_records_to_table(
            [
                {
                    "record_id": "collect-1",
                    "guid": "guid-superseded",
                    "changeset": "cs-1",
                    "last_modified": fact_time,
                },
                # Reclaimed: this guid is an active mapping again, so the
                # fact must be filtered out.
                {
                    "record_id": "collect-2",
                    "guid": "guid-reclaimed",
                    "changeset": "cs-1",
                },
            ],
            namespace=NAMESPACE,
        )
    )
    ReconcilerStore(reconciler_temporary_table, namespace=NAMESPACE).incremental_update(
        reconciler_records_to_table(
            [{"id": "collect-2", "guid": "guid-reclaimed"}], namespace=NAMESPACE
        )
    )

    deletions = list(
        _reader(
            temporary_table,
            ["cs-1"],
            facts_table=deletion_facts_temporary_table,
            reconciler_table=reconciler_temporary_table,
        ).iter_deletions()
    )

    assert deletions == [
        SupersededGuid(
            fact_id="collect-1/cs-1",
            record_id="collect-1",
            guid="guid-superseded",
            changeset_id="cs-1",
            last_modified=fact_time,
        )
    ]


def test_no_changesets_yields_no_deletions_and_reads_no_facts(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    reader = _reader(
        temporary_table,
        [],
        facts_table=deletion_facts_temporary_table,
        reconciler_table=reconciler_temporary_table,
    )
    assert reader.facts_store is not None

    def fail_read(*args: Any, **kwargs: Any) -> None:
        raise AssertionError("facts must not be read without changesets")

    monkeypatch.setattr(reader.facts_store, "get_records_by_changesets", fail_read)
    assert list(reader.iter_deletions()) == []


def test_ids_mode_yields_no_deletions_and_reads_no_facts(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """An id-mode run only re-transforms named records; it does not replay
    superseded-guid deletion facts, which are keyed by changeset, not id."""
    reader = _reader(
        temporary_table,
        [],
        ids=["collect-1"],
        facts_table=deletion_facts_temporary_table,
        reconciler_table=reconciler_temporary_table,
    )
    assert reader.facts_store is not None

    def fail_read(*args: Any, **kwargs: Any) -> None:
        raise AssertionError("facts must not be read during an id-mode run")

    monkeypatch.setattr(reader.facts_store, "get_records_by_changesets", fail_read)
    assert list(reader.iter_deletions()) == []


def test_facts_store_requires_reconciler_store(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
) -> None:
    with pytest.raises(ValueError, match="provided together"):
        _reader(
            temporary_table,
            ["cs-1"],
            facts_table=deletion_facts_temporary_table,
        )


class _CountingConfig:
    """Table builder that records which tables were requested."""

    adapter_namespace = NAMESPACE

    def __init__(self, adapter_table: IcebergTable):
        self.adapter_table = adapter_table
        self.adapter_builds = 0

    def build_adapter_table(self, **kwargs: Any) -> IcebergTable:
        self.adapter_builds += 1
        return self.adapter_table

    def build_deletion_facts_table(self, **kwargs: Any) -> IcebergTable:
        raise AssertionError("facts table must not be built")

    def build_reconciler_table(self, **kwargs: Any) -> IcebergTable:
        raise AssertionError("reconciler table must not be built")


def test_build_without_deletion_facts_builds_no_facts_tables(
    temporary_table: IcebergTable,
) -> None:
    config = _CountingConfig(temporary_table)
    reader = AxiellChangesetReader.build(
        config,
        ["cs-1"],
        use_rest_api_table=False,
        with_deletion_facts=False,
    )
    assert reader.facts_store is None
    with pytest.raises(RuntimeError, match="without deletion facts"):
        list(reader.iter_deletions())


def test_build_reuses_injected_adapter_store(temporary_table: IcebergTable) -> None:
    config = _CountingConfig(temporary_table)
    adapter_store = AdapterStore(temporary_table, namespace=NAMESPACE)
    reader = AxiellChangesetReader.build(
        config,
        [],
        use_rest_api_table=False,
        adapter_store=adapter_store,
    )
    assert reader.adapter_store is adapter_store
    assert config.adapter_builds == 0


def test_build_threads_ids_onto_reader(temporary_table: IcebergTable) -> None:
    config = _CountingConfig(temporary_table)
    reader = AxiellChangesetReader.build(
        config,
        [],
        use_rest_api_table=False,
        ids=["collect-1", "collect-2"],
    )
    assert reader.ids == ["collect-1", "collect-2"]
    assert reader.changeset_ids == []
