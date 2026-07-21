from typing import Any

import pytest
from pyiceberg.exceptions import NoSuchTableError
from pyiceberg.table import Table as IcebergTable

from adapters.steps.transformer import TransformerEvent, TransformerResult, handler
from adapters.transformers.axiell_store_source import AxiellStoreSource
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore
from tests.adapters.conftest import (
    deletion_facts_records_to_table,
    reconciler_records_to_table,
)
from tests.adapters.extractors.ebsco.helpers import prepare_changeset
from tests.adapters.transformers.conftest import read_transformer_report
from tests.mocks import MockElasticsearchClient

AXIELL_NAMESPACE = "axiell"


def _marcxml(guid: str) -> str:
    return (
        "<record><leader>00000nam a2200000   4500</leader>"
        "<controlfield tag='005'>20251225123045.0</controlfield>"
        f"<controlfield tag='001'>{guid}</controlfield>"
        "<datafield tag='245' ind1='0' ind2='0'>"
        f"<subfield code='a'>Title for {guid}</subfield></datafield>"
        "<datafield tag='035'><subfield code='a'>(Calm RefNo)A/B</subfield></datafield>"
        "<datafield tag='351'><subfield code='c'>item</subfield></datafield>"
        "<datafield tag='583' ind1='0'><subfield code='l'>catalogued</subfield></datafield>"
        "</record>"
    )


def _seed_facts(
    deletion_facts_temporary_table: IcebergTable, facts: list[dict[str, Any]]
) -> None:
    store = DeletionFactsStore(
        deletion_facts_temporary_table, namespace=AXIELL_NAMESPACE
    )
    store.append_facts(
        deletion_facts_records_to_table(facts, namespace=AXIELL_NAMESPACE)
    )


def _seed_mappings(
    reconciler_temporary_table: IcebergTable, mappings: dict[str, str]
) -> None:
    store = ReconcilerStore(reconciler_temporary_table, namespace=AXIELL_NAMESPACE)
    store.incremental_update(
        reconciler_records_to_table(
            [{"id": record_id, "guid": guid} for record_id, guid in mappings.items()],
            namespace=AXIELL_NAMESPACE,
        )
    )


def _run_transform(
    monkeypatch: pytest.MonkeyPatch,
    changeset_ids: list[str],
    facts_table: IcebergTable | None = None,
    reconciler_table: IcebergTable | None = None,
) -> TransformerResult:
    if facts_table is not None:
        monkeypatch.setattr(
            "adapters.steps.transformer.AXIELL_CONFIG.build_deletion_facts_table",
            lambda **kwargs: facts_table,
        )
    if reconciler_table is not None:
        monkeypatch.setattr(
            "adapters.steps.transformer.AXIELL_CONFIG.build_reconciler_table",
            lambda **kwargs: reconciler_table,
        )

    event = TransformerEvent(
        transformer_type="axiell",
        job_id="test-job-id",
        changeset_ids=changeset_ids,
    )

    return handler(event=event, es_mode="local", use_rest_api_table=False)


def test_facts_delivered_as_tombstones_alongside_adapter_rows(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"collect-1": _marcxml("guid-new-1")},
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )
    _seed_facts(
        deletion_facts_temporary_table,
        [{"record_id": "collect-1", "guid": "guid-old-1", "changeset": changeset_id}],
    )
    # Post-detection state: the record's mapping already points at the new guid
    _seed_mappings(reconciler_temporary_table, {"collect-1": "guid-new-1"})

    result = _run_transform(
        monkeypatch,
        [changeset_id],
        facts_table=deletion_facts_temporary_table,
        reconciler_table=reconciler_temporary_table,
    )

    assert result.success_count == 2
    assert result.failure_count == 0

    docs_by_id = {op["_id"]: op["_source"] for op in MockElasticsearchClient.inputs}
    assert set(docs_by_id) == {
        "Work[axiell-guid/guid-new-1]",
        "Work[axiell-guid/guid-old-1]",
    }
    tombstone = docs_by_id["Work[axiell-guid/guid-old-1]"]
    assert tombstone["type"] == "Deleted"
    assert tombstone["deletedReason"]["type"] == "DeletedFromSource"

    # The fact delivery is reported alongside the adapter row's work.
    report = read_transformer_report(result)
    assert sorted(report["successful_ids"]) == sorted(
        ["Work[axiell-guid/guid-new-1]", "Work[axiell-guid/guid-old-1]"]
    )


def test_facts_from_other_changesets_are_not_delivered(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"collect-1": _marcxml("guid-new-1")},
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )
    _seed_facts(
        deletion_facts_temporary_table,
        [
            {
                "record_id": "collect-1",
                "guid": "guid-old-1",
                "changeset": changeset_id,
            },
            {
                "record_id": "collect-2",
                "guid": "guid-old-2",
                "changeset": "some-other-changeset",
            },
        ],
    )

    result = _run_transform(
        monkeypatch,
        [changeset_id],
        facts_table=deletion_facts_temporary_table,
        reconciler_table=reconciler_temporary_table,
    )

    assert result.success_count == 2
    assert {op["_id"] for op in MockElasticsearchClient.inputs} == {
        "Work[axiell-guid/guid-new-1]",
        "Work[axiell-guid/guid-old-1]",
    }


def test_es_error_on_tombstone_reported_under_fact_id(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"collect-1": _marcxml("guid-new-1")},
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )
    _seed_facts(
        deletion_facts_temporary_table,
        [{"record_id": "collect-1", "guid": "guid-old-1", "changeset": changeset_id}],
    )

    def fake_bulk(client, actions, raise_on_error, stats_only):  # type: ignore[no-untyped-def]
        actions_list = list(actions)
        failed_id = "Work[axiell-guid/guid-old-1]"
        errors = [
            {
                "index": {
                    "_id": action["_id"],
                    "status": 400,
                    "error": {"type": "mapper_parsing_exception"},
                }
            }
            for action in actions_list
            if action["_id"] == failed_id
        ]
        return len(actions_list) - len(errors), errors

    monkeypatch.setattr("elasticsearch.helpers.bulk", fake_bulk)

    result = _run_transform(
        monkeypatch,
        [changeset_id],
        facts_table=deletion_facts_temporary_table,
        reconciler_table=reconciler_temporary_table,
    )

    assert result.success_count == 1
    assert result.failure_count == 1

    errors = read_transformer_report(result)["errors"]
    assert len(errors) == 1
    assert errors[0]["row_id"] == f"collect-1/{changeset_id}"
    assert errors[0]["stage"] == "index"


def test_fact_transform_error_reported_and_run_continues(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"collect-1": _marcxml("guid-new-1")},
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )
    _seed_facts(
        deletion_facts_temporary_table,
        [{"record_id": "collect-1", "guid": "guid-old-1", "changeset": changeset_id}],
    )

    def broken_builder(*args: Any, **kwargs: Any) -> None:
        raise ValueError("broken fact")

    monkeypatch.setattr(
        "adapters.transformers.axiell_transformer.ReconcilerWorkBuilder",
        broken_builder,
    )

    result = _run_transform(
        monkeypatch,
        [changeset_id],
        facts_table=deletion_facts_temporary_table,
        reconciler_table=reconciler_temporary_table,
    )

    # The adapter row's work is still transformed and indexed.
    assert result.success_count == 1
    assert [op["_id"] for op in MockElasticsearchClient.inputs] == [
        "Work[axiell-guid/guid-new-1]"
    ]

    assert result.failure_count == 1
    errors = read_transformer_report(result)["errors"]
    assert len(errors) == 1
    assert errors[0]["row_id"] == f"collect-1/{changeset_id}"
    assert errors[0]["stage"] == "transform"


def test_full_reindex_never_builds_or_reads_the_facts_store(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    prepare_changeset(
        temporary_table,
        monkeypatch,
        {"collect-1": _marcxml("guid-new-1")},
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )

    def fail_build(**kwargs: Any) -> IcebergTable:
        raise AssertionError("facts table must not be built during a full reindex")

    monkeypatch.setattr(
        "adapters.steps.transformer.AXIELL_CONFIG.build_deletion_facts_table",
        fail_build,
    )

    result = _run_transform(monkeypatch, changeset_ids=[])

    assert result.success_count == 1
    assert [op["_id"] for op in MockElasticsearchClient.inputs] == [
        "Work[axiell-guid/guid-new-1]"
    ]

    # Even with a facts store attached, the source must not read facts when
    # there are no changeset ids.
    facts_store = DeletionFactsStore(
        deletion_facts_temporary_table, namespace=AXIELL_NAMESPACE
    )

    def fail_read(*args: Any, **kwargs: Any) -> None:
        raise AssertionError("facts must not be read during a full reindex")

    monkeypatch.setattr(facts_store, "get_records_by_changesets", fail_read)
    source = AxiellStoreSource(
        AdapterStore(temporary_table, namespace=AXIELL_NAMESPACE),
        changeset_ids=[],
        facts_store=facts_store,
        reconciler_store=ReconcilerStore(
            reconciler_temporary_table, namespace=AXIELL_NAMESPACE
        ),
    )
    assert [row["id"] for row in source.stream_raw()] == ["collect-1"]


def test_stale_fact_for_reclaimed_guid_is_skipped(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """A fact whose guid is once again an active mapping (guid revert or
    handoff after detection, surfaced by re-driving an old changeset) must not
    tombstone the live work now indexed under that guid."""
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"collect-3": _marcxml("guid-3")},
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )
    _seed_facts(
        deletion_facts_temporary_table,
        [
            {"record_id": "collect-1", "guid": "guid-old-1", "changeset": changeset_id},
            {"record_id": "collect-2", "guid": "guid-old-2", "changeset": changeset_id},
        ],
    )
    # collect-1 has since reverted to guid-old-1; guid-old-2 stays unclaimed
    _seed_mappings(reconciler_temporary_table, {"collect-1": "guid-old-1"})

    result = _run_transform(
        monkeypatch,
        [changeset_id],
        facts_table=deletion_facts_temporary_table,
        reconciler_table=reconciler_temporary_table,
    )

    assert result.success_count == 2
    assert result.failure_count == 0
    assert {op["_id"] for op in MockElasticsearchClient.inputs} == {
        "Work[axiell-guid/guid-3]",
        "Work[axiell-guid/guid-old-2]",
    }


def test_facts_store_requires_reconciler_store(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
) -> None:
    with pytest.raises(ValueError, match="provided together"):
        AxiellStoreSource(
            AdapterStore(temporary_table, namespace=AXIELL_NAMESPACE),
            changeset_ids=["some-changeset"],
            facts_store=DeletionFactsStore(
                deletion_facts_temporary_table, namespace=AXIELL_NAMESPACE
            ),
        )


def test_missing_reconciler_table_fails_the_transform(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"collect-1": _marcxml("guid-new-1")},
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )

    def missing_table(**kwargs: Any) -> IcebergTable:
        raise NoSuchTableError("no such table")

    monkeypatch.setattr(
        "adapters.steps.transformer.AXIELL_CONFIG.build_reconciler_table",
        missing_table,
    )

    with pytest.raises(NoSuchTableError):
        _run_transform(
            monkeypatch, [changeset_id], facts_table=deletion_facts_temporary_table
        )


def test_missing_facts_table_fails_the_transform(
    temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"collect-1": _marcxml("guid-new-1")},
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )

    def missing_table(**kwargs: Any) -> IcebergTable:
        raise NoSuchTableError("no such table")

    monkeypatch.setattr(
        "adapters.steps.transformer.AXIELL_CONFIG.build_deletion_facts_table",
        missing_table,
    )

    with pytest.raises(NoSuchTableError):
        _run_transform(monkeypatch, [changeset_id])
