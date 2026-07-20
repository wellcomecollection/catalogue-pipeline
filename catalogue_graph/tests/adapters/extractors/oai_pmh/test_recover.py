"""Tests for the OAI-PMH recover-by-id step."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import cast
from unittest.mock import MagicMock

import pyarrow as pa
import pytest
from lxml import etree
from oai_pmh_client.client import OAIClient
from oai_pmh_client.exceptions import IdDoesNotExistError
from oai_pmh_client.models import Header, Record

from adapters.steps.oai_pmh import recover
from adapters.steps.oai_pmh.recover import RecoverEvent, RecoverRuntime, RecoveryBatch
from adapters.utils.adapter_store import AdapterStore


def _empty_body_error() -> etree.XMLSyntaxError:
    """A real XMLSyntaxError, as parsing an empty OAI body raises."""
    try:
        etree.fromstring(b"")
    except etree.XMLSyntaxError as exc:
        return exc
    raise AssertionError("parsing empty content should have raised")


@pytest.fixture(autouse=True)
def _no_polite_delay(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(recover, "POLITE_DELAY_SECONDS", 0)


def _record(identifier: str) -> Record:
    header = Header(
        identifier=identifier,
        datestamp=datetime(2025, 1, 1, tzinfo=UTC),
        setSpec=["collect"],
        status=False,
    )
    metadata = etree.fromstring(f"<meta><id>{identifier}</id></meta>")
    return Record(header=header, metadata=metadata)


def _runtime(get_record: object) -> tuple[RecoverRuntime, list[pa.Table]]:
    oai = MagicMock(spec=OAIClient)
    oai.get_record.side_effect = get_record
    store = MagicMock(spec=AdapterStore)
    commits: list[pa.Table] = []
    store.incremental_update.side_effect = lambda table: commits.append(table)
    runtime = RecoverRuntime(
        oai_client=oai,
        store=store,
        adapter_name="axiell",
        namespace="axiell",
        metadata_prefix="oai_marcxml",
    )
    return runtime, commits


class TestRecoverEvent:
    def test_parses_a_minimal_event(self) -> None:
        event = RecoverEvent.model_validate(
            {"adapter_type": "axiell", "ids": ["collect:1"]}
        )
        assert event.adapter_type == "axiell"
        assert event.ids == ["collect:1"]
        assert event.commit_every == recover.DEFAULT_COMMIT_EVERY
        # These steps are invoked ad hoc, so they belong to no harvest job.
        assert event.job_id is None

    def test_commit_every_is_coerced_from_a_string(self) -> None:
        event = RecoverEvent.model_validate(
            {"adapter_type": "axiell", "ids": ["collect:1"], "commit_every": "50"}
        )
        assert event.commit_every == 50

    @pytest.mark.parametrize(
        "event",
        [
            {"ids": ["collect:1"]},
            {"adapter_type": "axiell"},
            {"adapter_type": "axiell", "ids": "collect:1"},
        ],
        ids=["missing adapter_type", "missing ids", "ids not a list"],
    )
    def test_rejects_malformed_events(self, event: dict) -> None:
        # ValidationError subclasses ValueError, so existing callers that catch
        # ValueError still see these as failures.
        with pytest.raises(ValueError):
            RecoverEvent.model_validate(event)


class TestRecoveryBatch:
    """The accumulator the handler drives, exercised directly."""

    def test_buffers_until_commit_every_then_commits(self) -> None:
        runtime, commits = _runtime(lambda **_: None)
        batch = RecoveryBatch(runtime, commit_every=2)

        batch.add_recovered("collect:1", _record("collect:1"))
        assert commits == []
        batch.add_recovered("collect:2", _record("collect:2"))
        assert [c.num_rows for c in commits] == [2]

    def test_flush_commits_the_remainder(self) -> None:
        runtime, commits = _runtime(lambda **_: None)
        batch = RecoveryBatch(runtime, commit_every=10)

        batch.add_recovered("collect:1", _record("collect:1"))
        batch.flush()
        assert [c.num_rows for c in commits] == [1]
        assert cast("list[str]", commits[0]["id"].to_pylist()) == ["collect:1"]

    def test_flush_on_an_empty_buffer_makes_no_commit(self) -> None:
        runtime, commits = _runtime(lambda **_: None)
        batch = RecoveryBatch(runtime)

        batch.flush()
        batch.flush()
        assert commits == []

    def test_classification_lists_stay_separate(self) -> None:
        runtime, _ = _runtime(lambda **_: None)
        batch = RecoveryBatch(runtime)

        batch.add_recovered("collect:1", _record("collect:1"))
        batch.add_removed("collect:gone")
        batch.add_unfetchable("collect:dead", _empty_body_error())

        assert batch.recovered == ["collect:1"]
        assert batch.removed == ["collect:gone"]
        assert batch.unfetchable == ["collect:dead"]

    def test_to_response_reports_the_accumulated_counts(self) -> None:
        runtime, _ = _runtime(lambda **_: None)
        batch = RecoveryBatch(runtime)

        batch.add_recovered("collect:1", _record("collect:1"))
        batch.add_removed("collect:gone")
        batch.add_unfetchable("collect:dead", _empty_body_error())
        response = batch.to_response(requested=3)

        assert response.adapter_type == "axiell"
        assert response.requested == 3
        assert response.recovered == 1
        assert response.removed == 1
        # Unfetchable ids are reported in full, not just counted.
        assert response.unfetchable == ["collect:dead"]


def test_recovers_writes_and_classifies() -> None:
    def get_record(*, identifier: str, metadata_prefix: str) -> Record:
        if identifier == "collect:gone":
            raise IdDoesNotExistError("deleted")
        if identifier == "collect:dead":
            raise _empty_body_error()
        return _record(identifier)

    runtime, commits = _runtime(get_record)
    response = recover.handler(
        ["collect:1", "collect:2", "collect:gone", "collect:dead"], runtime
    )

    assert response.recovered == 2
    assert response.removed == 1
    assert response.unfetchable == ["collect:dead"]
    # One commit, holding the two recovered rows.
    assert len(commits) == 1
    committed_ids = cast("list[str]", commits[0]["id"].to_pylist())
    assert sorted(committed_ids) == ["collect:1", "collect:2"]


def test_batches_commits_by_commit_every() -> None:
    runtime, commits = _runtime(
        lambda *, identifier, metadata_prefix: _record(identifier)
    )
    response = recover.handler(
        [f"collect:{i}" for i in range(5)], runtime, commit_every=2
    )
    assert response.recovered == 5
    # 5 records at commit_every=2 -> commits of 2, 2, then a final flush of 1.
    assert [c.num_rows for c in commits] == [2, 2, 1]


def test_dedupes_ids() -> None:
    runtime, commits = _runtime(
        lambda *, identifier, metadata_prefix: _record(identifier)
    )
    response = recover.handler(["collect:1", "collect:1", "collect:2"], runtime)
    assert response.requested == 2
    assert response.recovered == 2


def test_no_recoverable_records_makes_no_commit() -> None:
    def get_record(*, identifier: str, metadata_prefix: str) -> Record:
        raise IdDoesNotExistError("gone")

    runtime, commits = _runtime(get_record)
    response = recover.handler(["collect:1"], runtime)
    assert response.removed == 1
    assert commits == []


def test_source_errors_classify_as_unfetchable_not_abort() -> None:
    import httpx as _httpx
    from oai_pmh_client.exceptions import OAIError

    errors = {
        "collect:oai": OAIError("protocol error"),
        "collect:net": _httpx.ConnectError("boom"),
        "collect:empty": _empty_body_error(),
    }

    def get_record(*, identifier: str, metadata_prefix: str) -> Record:
        if identifier in errors:
            raise errors[identifier]
        return _record(identifier)

    runtime, commits = _runtime(get_record)
    response = recover.handler(
        ["collect:oai", "collect:1", "collect:net", "collect:empty"], runtime
    )
    # The run completes and recovers the good id rather than aborting.
    assert response.recovered == 1
    assert sorted(response.unfetchable) == [
        "collect:empty",
        "collect:net",
        "collect:oai",
    ]
