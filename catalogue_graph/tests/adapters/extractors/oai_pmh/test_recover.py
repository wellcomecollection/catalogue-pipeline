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
from adapters.steps.oai_pmh.recover import RecoverRuntime
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
