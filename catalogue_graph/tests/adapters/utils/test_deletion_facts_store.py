"""
Tests covering the append-only behaviour of the DeletionFactsStore.

Facts carry a deterministic id ("{record_id}/{changeset_id}") and the
triggering adapter changeset id; append_facts must preserve both, deduplicate
retried writes, and never overwrite existing rows.
"""

from datetime import UTC, datetime
from typing import cast

from pyiceberg.table import Table as IcebergTable

from adapters.utils.deletion_facts_store import DeletionFactsStore
from tests.adapters.conftest import deletion_facts_records_to_table


def test_append_facts_preserves_explicit_changeset(
    deletion_facts_temporary_table: IcebergTable,
) -> None:
    """
    Given facts tagged with their triggering adapter changeset ids
    When they are appended
    Then the stored rows keep those changeset ids (no re-minting)
    """
    facts = deletion_facts_records_to_table(
        [
            {"record_id": "rec001", "guid": "guid-old-1", "changeset": "changeset-a"},
            {"record_id": "rec002", "guid": "guid-old-2", "changeset": "changeset-b"},
        ]
    )

    store = DeletionFactsStore(deletion_facts_temporary_table, "test_namespace")
    appended = store.append_facts(facts)
    assert appended == 2

    rows = {row["id"]: row for row in store.get_all_records().to_pylist()}
    assert rows["rec001/changeset-a"]["changeset"] == "changeset-a"
    assert rows["rec002/changeset-b"]["changeset"] == "changeset-b"


def test_append_facts_deduplicates_on_retry(
    deletion_facts_temporary_table: IcebergTable,
) -> None:
    """
    Given facts that were already appended
    When the same facts are appended again (a retried write)
    Then no duplicate rows are written and only new rows are counted
    """
    facts = deletion_facts_records_to_table(
        [
            {"record_id": "rec001", "guid": "guid-old-1", "changeset": "changeset-a"},
            {"record_id": "rec002", "guid": "guid-old-2", "changeset": "changeset-a"},
        ]
    )

    store = DeletionFactsStore(deletion_facts_temporary_table, "test_namespace")
    assert store.append_facts(facts) == 2

    retried = deletion_facts_records_to_table(
        [
            {"record_id": "rec001", "guid": "guid-old-1", "changeset": "changeset-a"},
            {"record_id": "rec002", "guid": "guid-old-2", "changeset": "changeset-a"},
            {"record_id": "rec003", "guid": "guid-old-3", "changeset": "changeset-a"},
        ]
    )
    assert store.append_facts(retried) == 1

    ids = cast(list[str], store.get_all_records().column("id").to_pylist())
    assert sorted(ids) == [
        "rec001/changeset-a",
        "rec002/changeset-a",
        "rec003/changeset-a",
    ]


def test_append_facts_is_append_only_across_changesets(
    deletion_facts_temporary_table: IcebergTable,
) -> None:
    """
    Given a record whose guid changed in two separate changesets
    When a fact is appended for each changeset
    Then both facts are kept as separate rows
    """
    store = DeletionFactsStore(deletion_facts_temporary_table, "test_namespace")
    assert (
        store.append_facts(
            deletion_facts_records_to_table(
                [
                    {
                        "record_id": "rec001",
                        "guid": "guid-old-1",
                        "changeset": "changeset-a",
                    }
                ]
            )
        )
        == 1
    )
    assert (
        store.append_facts(
            deletion_facts_records_to_table(
                [
                    {
                        "record_id": "rec001",
                        "guid": "guid-old-2",
                        "changeset": "changeset-b",
                    }
                ]
            )
        )
        == 1
    )

    rows = {row["id"]: row for row in store.get_all_records().to_pylist()}
    assert sorted(rows) == ["rec001/changeset-a", "rec001/changeset-b"]
    assert rows["rec001/changeset-a"]["guid"] == "guid-old-1"
    assert rows["rec001/changeset-b"]["guid"] == "guid-old-2"


def test_get_records_by_changesets_returns_facts_for_requested_changesets(
    deletion_facts_temporary_table: IcebergTable,
) -> None:
    """
    Given facts across several changesets
    When facts are read by changeset ids (the delivery read)
    Then exactly the facts for the requested changesets are returned
    """
    earlier = datetime(2026, 7, 1, 9, 0, tzinfo=UTC)
    later = datetime(2026, 7, 1, 10, 0, tzinfo=UTC)
    facts = deletion_facts_records_to_table(
        [
            {
                "record_id": "rec001",
                "guid": "guid-old-1",
                "changeset": "changeset-a",
                "last_modified": earlier,
            },
            {
                "record_id": "rec002",
                "guid": "guid-old-2",
                "changeset": "changeset-b",
                "last_modified": later,
            },
            {
                "record_id": "rec003",
                "guid": "guid-old-3",
                "changeset": "changeset-c",
                "last_modified": earlier,
            },
        ]
    )

    store = DeletionFactsStore(deletion_facts_temporary_table, "test_namespace")
    assert store.append_facts(facts) == 3

    result = store.get_records_by_changesets(["changeset-a", "changeset-b"])
    ids = cast(list[str], result.column("id").to_pylist())
    assert sorted(ids) == ["rec001/changeset-a", "rec002/changeset-b"]

    assert store.get_records_by_changesets(["changeset-missing"]).num_rows == 0
