"""Tests for the adapter transformer's work-discovery step."""

import pytest
from pydantic import ValidationError
from structlog.testing import capture_logs

from adapters.steps import transformer_find_work
from adapters.steps.transformer import TransformerEvent
from adapters.steps.transformer_find_work import parse_event, plan
from adapters.utils.adapter_store import AdapterStore
from tests.adapters.conftest import AdapterStoreFactory
from tests.mocks import MockSmartOpen
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

    partitions, row_counts = plan(
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
    assert row_counts == [6, 4]
    assert [p.job_id for p in partitions] == ["job-1-p000", "job-1-p001"]
    assert {p.snapshot_id for p in partitions} == {store.current_snapshot_id()}
    assert all(p.ids is None for p in partitions)
    assert all(p.with_deletion_facts for p in partitions)


def test_reindex_plans_every_changeset_in_the_store(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    store = _store(adapter_store_with_records)

    partitions, _ = plan(_event(full=True), store)

    planned = [c for p in partitions for c in p.changeset_ids]
    assert planned == ["changeset-1", "changeset-2", "changeset-3"]
    assert not any(p.with_deletion_facts for p in partitions)


def test_reindex_skips_the_deletion_limits(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    def fail_count(changeset_ids: list[str]) -> int:
        raise AssertionError("a reindex must not count deletion facts")

    partitions, _ = plan(
        _event(full=True, max_tombstones=0),
        _store(adapter_store_with_records),
        fail_count,
    )

    assert partitions


def test_an_explicitly_empty_changeset_list_plans_nothing(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    assert plan(_event(changeset_ids=[]), _store(adapter_store_with_records)) == (
        [],
        [],
    )


def test_a_changeset_with_no_rows_left_is_still_planned(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    store = _store(adapter_store_with_records)

    partitions, _ = plan(_event(changeset_ids=["changeset-gone"]), store)

    assert [p.changeset_ids for p in partitions] == [["changeset-gone"]]


@pytest.mark.parametrize(
    "fields",
    [
        {},
        {"changeset_ids": ["changeset-1"], "full": True},
        {"changeset_ids": [], "full": True},
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


def test_too_many_deletion_facts_stops_before_fan_out(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    counted: list[list[str]] = []

    def count(changeset_ids: list[str]) -> int:
        counted.append(changeset_ids)
        return 3

    store = _store(adapter_store_with_records)
    event = _event(changeset_ids=["changeset-1"], max_deletion_facts=2)

    with pytest.raises(RuntimeError, match="deletion facts"):
        plan(event, store, count)
    assert counted == [["changeset-1"]]
    assert plan(
        _event(changeset_ids=["changeset-1"], max_deletion_facts=3), store, count
    )[0]


def test_oversized_changesets_are_logged(
    adapter_store_with_records: AdapterStoreFactory,
) -> None:
    with capture_logs() as logs:
        plan(
            _event(changeset_ids=["changeset-1", "changeset-3"], partition_rows=3),
            _store(adapter_store_with_records),
        )

    warnings = [log for log in logs if log["log_level"] == "warning"]
    assert [log["changeset_ids"] for log in warnings] == [["changeset-3"]]


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
    assert "/ebsco/dev/find_work/job-1/" in refs[0]["s3_uri"]
    assert refs[0]["s3_uri"].endswith("/partition-0.json")
    resolved = pydantic_from_s3_json(TransformerEvent, refs[0]["s3_uri"])
    assert resolved.changeset_ids == ["changeset-1", "changeset-2", "changeset-3"]
    assert resolved.snapshot_id == store.current_snapshot_id()


def test_lambda_handler_with_nothing_planned_writes_nothing(
    adapter_store_with_records: AdapterStoreFactory,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    store = _store(adapter_store_with_records)
    monkeypatch.setattr(
        transformer_find_work, "get_adapter_store", lambda *args, **kwargs: store
    )

    result = transformer_find_work.lambda_handler(
        {
            "detail": {
                "transformer_type": "ebsco",
                "job_id": "job-1",
                "changeset_ids": [],
            }
        },
        _LambdaContext(),
    )

    assert result["partitions"] == []
    assert not [uri for uri in MockSmartOpen.file_lookup if "/find_work/" in uri]


def test_events_sharing_a_job_id_write_to_different_paths(
    adapter_store_with_records: AdapterStoreFactory,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    store = _store(adapter_store_with_records)
    monkeypatch.setattr(
        transformer_find_work, "get_adapter_store", lambda *args, **kwargs: store
    )

    def first_uri(changeset_id: str) -> str:
        result = transformer_find_work.lambda_handler(
            {
                "detail": {
                    "transformer_type": "ebsco",
                    "job_id": "rebuild-1",
                    "changeset_ids": [changeset_id],
                }
            },
            _LambdaContext(),
        )
        uri: str = result["partitions"][0]["s3_uri"]
        return uri

    one, two = first_uri("changeset-1"), first_uri("changeset-2")

    assert one != two
    assert pydantic_from_s3_json(TransformerEvent, one).changeset_ids == ["changeset-1"]
    assert pydantic_from_s3_json(TransformerEvent, two).changeset_ids == ["changeset-2"]
    assert first_uri("changeset-1") == one
