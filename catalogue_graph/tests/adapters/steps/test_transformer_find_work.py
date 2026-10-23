"""Tests for the adapter transformer's work-discovery step."""

import pytest
from pydantic import ValidationError

from adapters.steps import transformer_find_work
from adapters.steps.transformer import TransformerEvent
from adapters.steps.transformer_find_work import parse_event, plan
from adapters.utils.adapter_store import AdapterStore
from tests.adapters.conftest import AdapterStoreFactory
from utils.aws import pydantic_from_s3_json


class _LambdaContext:
    aws_request_id = "req-abc123"


def _store(adapter_store_with_records: AdapterStoreFactory) -> AdapterStore:
    rows: list[dict[str, object]] = []
    for changeset, count in (
        ("changeset-1", 3),
        ("changeset-2", 2),
        ("changeset-3", 4),
    ):
        rows += [
            {"id": f"{changeset}-rec{i}", "content": "x", "changeset": changeset}
            for i in range(count)
        ]
    rows.append(
        {
            "id": "deleted-rec",
            "content": "x",
            "changeset": "changeset-2",
            "deleted": True,
        }
    )
    return adapter_store_with_records(rows)


def _event(**fields: object) -> transformer_find_work.TransformerFindWorkEvent:
    return parse_event({"transformer_type": "ebsco", "job_id": "job-1", **fields})


def test_changeset_event_plans_groups_of_whole_changesets_at_one_snapshot(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    store = _store(adapter_store_with_records)

    partitions = plan(
        _event(
            changeset_ids=["changeset-1", "changeset-2", "changeset-3"],
            partition_rows=6,
        ),
        store,
    )

    assert [p.changeset_ids for p in partitions] == [
        ["changeset-1", "changeset-2"],
        ["changeset-3"],
    ]
    assert [p.job_id for p in partitions] == ["job-1-p000", "job-1-p001"]
    assert {p.snapshot_id for p in partitions} == {store.current_snapshot_id()}
    assert all(p.ids is None for p in partitions)


def test_reindex_plans_every_changeset_in_the_store(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    store = _store(adapter_store_with_records)

    partitions = plan(_event(full=True), store)

    planned = [c for p in partitions for c in p.changeset_ids]
    assert planned == ["changeset-1", "changeset-2", "changeset-3"]


def test_a_changeset_with_no_rows_left_is_still_planned(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    store = _store(adapter_store_with_records)

    partitions = plan(_event(changeset_ids=["changeset-gone"]), store)

    assert [p.changeset_ids for p in partitions] == [["changeset-gone"]]


@pytest.mark.parametrize(
    "fields",
    [
        {},
        {"changeset_ids": ["changeset-1"], "full": True},
        {"changeset_id": "changeset-1"},
        {"changeset_ids": ["changeset-1"], "partition_rows": 0},
    ],
)
def test_malformed_events_fail_instead_of_planning_nothing(fields: dict) -> None:
    with pytest.raises(ValidationError):
        _event(**fields)


def test_too_many_tombstones_stops_before_fan_out(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    store = _store(adapter_store_with_records)

    with pytest.raises(RuntimeError, match="tombstones"):
        plan(_event(changeset_ids=["changeset-2"], max_tombstones=0), store)


def test_parse_event_unwraps_the_rule_payload_and_ignores_extra_fields() -> None:
    event = parse_event(
        {
            "detail": {
                "transformer_type": "folio",
                "job_id": "20261020T1500",
                "changeset_ids": ["changeset-1"],
                "items_changeset_ids": ["items-1"],
            }
        }
    )

    assert event.transformer_type == "folio"
    assert event.changeset_ids == ["changeset-1"]


def test_lambda_handler_writes_partitions_to_s3_and_returns_refs(
    adapter_store_with_records: AdapterStoreFactory,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    store = _store(adapter_store_with_records)
    monkeypatch.setattr(
        transformer_find_work, "get_adapter_store", lambda *args, **kwargs: store
    )

    result = transformer_find_work.lambda_handler(
        {"detail": {"transformer_type": "ebsco", "job_id": "job-1", "full": True}},
        _LambdaContext(),
    )

    refs = result["partitions"]
    assert len(refs) == 1
    assert refs[0]["s3_uri"].endswith("/ebsco/dev/find_work/job-1/partition-0.json")
    resolved = pydantic_from_s3_json(TransformerEvent, refs[0]["s3_uri"])
    assert resolved.changeset_ids == ["changeset-1", "changeset-2", "changeset-3"]
    assert resolved.snapshot_id == store.current_snapshot_id()
