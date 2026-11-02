"""The planner's partitions must index exactly what one run over the event does.

The planner splits an event's changesets across partitions, so these compare
the Elasticsearch documents from one changeset run over the whole event with
those from its planned partitions, tombstones and Axiell deletion facts included.
"""

from datetime import UTC, datetime
from typing import Any

import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.extractors.ebsco.config as ebsco_config
from adapters.steps.ebsco.loader import EBSCO_NAMESPACE
from adapters.steps.transformer import TransformerEvent, TransformerResult, handler
from adapters.steps.transformer_find_work import parse_event, plan
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.deletion_facts_store import DeletionFactsStore
from adapters.utils.reconciler_store import ReconcilerStore
from tests.adapters.conftest import (
    deletion_facts_records_to_table,
    reconciler_records_to_table,
)
from tests.adapters.extractors.ebsco.helpers import prepare_changeset
from tests.mocks import MockElasticsearchClient

AXIELL_NAMESPACE = "axiell"


def _ebsco_marc(record_id: str, title: str) -> str:
    return (
        "<record><leader>00000nam a2200000   4500</leader>"
        f"<controlfield tag='001'>{record_id}</controlfield>"
        f"<datafield tag='245' ind1='0' ind2='0'><subfield code='a'>{title}</subfield>"
        "</datafield></record>"
    )


def _axiell_marc(guid: str) -> str:
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


def _without_transform_time(value: Any) -> Any:
    """Drop modifiedTime, the wall-clock time of the transform itself; the
    record's sourceModifiedTime and version are compared as they are."""
    if isinstance(value, dict):
        return {
            k: _without_transform_time(v)
            for k, v in value.items()
            if k != "modifiedTime"
        }
    if isinstance(value, list):
        return [_without_transform_time(v) for v in value]
    return value


def _indexed(events: list[TransformerEvent]) -> tuple[dict[str, Any], int]:
    """Run each event through the transformer; return its docs and success count."""
    MockElasticsearchClient.inputs.clear()
    results: list[TransformerResult] = [
        handler(event=event, es_mode="local", use_rest_api_table=False)
        for event in events
    ]
    docs = {
        op["_id"]: _without_transform_time(op["_source"])
        for op in MockElasticsearchClient.inputs
    }
    return docs, sum(r.success_count for r in results)


def test_ebsco_partitions_index_the_same_works_and_tombstones(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(ebsco_config, "PIPELINE_DATE", "dev")
    monkeypatch.setattr(ebsco_config, "INDEX_DATE", "2025-01-01")
    first = prepare_changeset(
        temporary_table,
        monkeypatch,
        {
            "ebs00001": _ebsco_marc("ebs00001", "Test record one"),
            "ebs00002": _ebsco_marc("ebs00002", "Test record two"),
        },
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )
    second = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"ebs00003": (_ebsco_marc("ebs00003", "Test record three"), True)},
        namespace=EBSCO_NAMESPACE,
        transformer_type="ebsco",
    )

    by_event, event_count = _indexed(
        [
            TransformerEvent(
                transformer_type="ebsco", job_id="job-1", changeset_ids=[first, second]
            )
        ]
    )
    partitions, _ = plan(
        parse_event(
            {
                "transformer_type": "ebsco",
                "job_id": "job-1",
                "changeset_ids": [first, second],
                "partition_rows": 2,
            }
        ),
        AdapterStore(temporary_table, EBSCO_NAMESPACE),
    )
    by_partition, partition_count = _indexed(partitions)

    assert [p.changeset_ids for p in partitions] == [[first], [second]]
    assert by_partition == by_event
    assert partition_count == event_count == 3
    assert by_partition["Work[ebsco-alt-lookup/ebs00003]"]["type"] == "Deleted"


def test_axiell_partitions_index_the_same_works_and_deletion_facts(
    temporary_table: IcebergTable,
    deletion_facts_temporary_table: IcebergTable,
    reconciler_temporary_table: IcebergTable,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"collect-1": _axiell_marc("guid-new-1")},
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )
    second = prepare_changeset(
        temporary_table,
        monkeypatch,
        {
            "collect-2": _axiell_marc("guid-new-2"),
            "collect-3": (_axiell_marc("guid-new-3"), True),
        },
        namespace=AXIELL_NAMESPACE,
        transformer_type="axiell",
    )
    DeletionFactsStore(
        deletion_facts_temporary_table, namespace=AXIELL_NAMESPACE
    ).append_facts(
        deletion_facts_records_to_table(
            [
                {
                    "record_id": "collect-1",
                    "guid": "guid-old-1",
                    "changeset": changeset_id,
                    "last_modified": datetime(2026, 7, 1, 12, 30, tzinfo=UTC),
                }
            ],
            namespace=AXIELL_NAMESPACE,
        )
    )
    ReconcilerStore(
        reconciler_temporary_table, namespace=AXIELL_NAMESPACE
    ).incremental_update(
        reconciler_records_to_table(
            [{"id": "collect-1", "guid": "guid-new-1"}], namespace=AXIELL_NAMESPACE
        )
    )
    monkeypatch.setattr(
        "adapters.steps.transformer.AXIELL_CONFIG.build_deletion_facts_table",
        lambda **kwargs: deletion_facts_temporary_table,
    )
    monkeypatch.setattr(
        "adapters.steps.transformer.AXIELL_CONFIG.build_reconciler_table",
        lambda **kwargs: reconciler_temporary_table,
    )

    by_event, event_count = _indexed(
        [
            TransformerEvent(
                transformer_type="axiell",
                job_id="job-1",
                changeset_ids=[changeset_id, second],
            )
        ]
    )
    partitions, _ = plan(
        parse_event(
            {
                "transformer_type": "axiell",
                "job_id": "job-1",
                "changeset_ids": [changeset_id, second],
                "partition_rows": 1,
            }
        ),
        AdapterStore(temporary_table, AXIELL_NAMESPACE),
    )
    by_partition, partition_count = _indexed(partitions)

    assert [p.changeset_ids for p in partitions] == [[changeset_id], [second]]
    assert by_partition == by_event
    assert partition_count == event_count == 4
    assert by_partition["Work[axiell-guid/guid-old-1]"]["type"] == "Deleted"
