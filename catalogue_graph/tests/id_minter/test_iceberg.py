"""Tests for the append-only Iceberg write of identified works."""

import json
from unittest.mock import MagicMock

import pytest
from pyiceberg.exceptions import CommitFailedException

from id_minter.iceberg import MAX_COMMIT_ATTEMPTS, append_identified_works
from merger.schemata import WORKS_IDENTIFIED_ICEBERG_SCHEMA


def _document(canonical_id: str, version: int = 3) -> dict:
    return {
        "type": "Visible",
        "version": version,
        "state": {
            "canonicalId": canonical_id,
            "sourceIdentifier": {
                "identifierType": {"id": "sierra-system-number"},
                "value": "b1000001",
                "ontologyType": "Work",
            },
            "sourceModifiedTime": "2024-09-24T19:26:50Z",
            "mergeCandidates": [{"id": {"canonicalId": "efgh5678"}}],
        },
        "data": {"title": "Test Work"},
    }


def _table(*append_outcomes: Exception | None) -> MagicMock:
    """A table whose append raises or succeeds in the given order."""
    table = MagicMock()
    table.schema.return_value = WORKS_IDENTIFIED_ICEBERG_SCHEMA
    table.append.side_effect = append_outcomes
    return table


@pytest.fixture(autouse=True)
def no_backoff(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("time.sleep", lambda seconds: None)


class TestAppendIdentifiedWorks:
    def test_nothing_to_write(self) -> None:
        table = _table()

        assert append_identified_works(table, []) == 0
        table.append.assert_not_called()

    def test_one_row_per_document(self) -> None:
        table = _table(None)
        documents = [_document("aaaa0001"), _document("aaaa0002", version=7)]

        assert append_identified_works(table, documents) == 2

        rows = table.append.call_args.args[0]
        assert rows.column("id").to_pylist() == ["aaaa0001", "aaaa0002"]
        assert rows.column("version").to_pylist() == [3, 7]
        assert rows.column("merge_candidate_ids").to_pylist() == [
            ["efgh5678"],
            ["efgh5678"],
        ]
        assert json.loads(rows.column("content")[0].as_py()) == documents[0]
        assert len(set(rows.column("last_modified").to_pylist())) == 1

    def test_retries_after_losing_the_commit(self) -> None:
        table = _table(CommitFailedException("snapshot moved"), None)

        assert append_identified_works(table, [_document("aaaa0001")]) == 1
        assert table.append.call_count == 2
        assert table.refresh.call_count == 2

    def test_gives_up_after_max_attempts(self) -> None:
        table = _table(*[CommitFailedException("snapshot moved")] * MAX_COMMIT_ATTEMPTS)

        with pytest.raises(CommitFailedException):
            append_identified_works(table, [_document("aaaa0001")])
        assert table.append.call_count == MAX_COMMIT_ATTEMPTS
