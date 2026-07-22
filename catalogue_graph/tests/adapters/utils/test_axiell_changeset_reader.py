from datetime import UTC, datetime
from typing import Any

import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.steps.axiell_folio_sync.mapper import extract, parse_xml
from adapters.steps.axiell_folio_sync.mapping import (
    _holdings_hrid,
    _instance_hrid,
    _item_hrid,
)
from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
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
from utils.marc import parse_single_marc_record

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
    facts_table: IcebergTable | None = None,
    reconciler_table: IcebergTable | None = None,
) -> AxiellChangesetReader:
    return AxiellChangesetReader(
        AdapterStore(adapter_table, namespace=NAMESPACE),
        changeset_ids,
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


def _seed_adapter_rows(table: IcebergTable, rows: list[dict[str, Any]]) -> None:
    store = AdapterStore(table, namespace=NAMESPACE)
    store.incremental_update(adapter_records_to_table(rows, namespace=NAMESPACE))


def test_records_pass_through_unchanged_including_tombstones(
    temporary_table: IcebergTable,
) -> None:
    _seed_adapter_rows(
        temporary_table,
        [
            {"id": "collect-1", "content": MARCXML},
            {"id": "collect-2", "content": MARCXML, "deleted": True},
        ],
    )
    store = AdapterStore(temporary_table, namespace=NAMESPACE)
    changeset_ids = list(
        {row["changeset"] for row in store.get_all_records().to_pylist()}
    )

    rows = {
        row["id"]: row for row in _reader(temporary_table, changeset_ids).iter_records()
    }

    assert set(rows) == {"collect-1", "collect-2"}
    assert rows["collect-2"]["deleted"] is True
    assert rows["collect-2"]["content"] == MARCXML


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
        namespace=NAMESPACE,
        with_deletion_facts=False,
    )
    assert reader.facts_store is None
    assert list(reader.iter_deletions()) == []


def test_build_reuses_injected_adapter_store(temporary_table: IcebergTable) -> None:
    config = _CountingConfig(temporary_table)
    adapter_store = AdapterStore(temporary_table, namespace=NAMESPACE)
    reader = AxiellChangesetReader.build(
        config,
        [],
        use_rest_api_table=False,
        namespace=NAMESPACE,
        adapter_store=adapter_store,
    )
    assert reader.adapter_store is adapter_store
    assert config.adapter_builds == 0


def test_superseded_guid_maps_directly_onto_folio_hrids() -> None:
    """The consumption point for platform#6440: a deletion fact's guid is the
    FOLIO source_id, so suppression targets derive with no adapter-row read."""
    deletion = SupersededGuid(
        fact_id="collect-1/cs-1",
        record_id="collect-1",
        guid="guid-001",
        changeset_id="cs-1",
        last_modified=datetime(2026, 7, 1, tzinfo=UTC),
    )
    assert _instance_hrid(deletion.guid) == "AxC-instance-guid-001"
    assert _holdings_hrid(deletion.guid) == "AxC-holding-guid-001"
    assert _item_hrid(deletion.guid) == "AxC-item-guid-001"


def test_fact_guid_and_folio_source_id_derivations_agree() -> None:
    """Pin the two 001 derivations together: the reconcile step's guid (via
    AxiellWorkBuilder / pymarc) and the sync's source_id (via mapper.extract)
    must stay byte-identical, or facts stop mapping onto FOLIO hrids."""
    work_builder_guid = AxiellWorkBuilder(
        parse_single_marc_record(MARCXML), datetime(2026, 7, 1, tzinfo=UTC)
    ).source_identifier.value
    sync_source_id = extract(parse_xml(MARCXML), "001")

    assert work_builder_guid == "guid-001"
    assert sync_source_id == work_builder_guid
