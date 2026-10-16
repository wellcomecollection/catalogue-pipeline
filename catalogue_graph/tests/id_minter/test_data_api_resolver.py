"""Tests for DataApiIdResolver.mint_ids() with its lookup stubbed, so no AWS is needed."""

import pytest

from id_minter.models.identifier import SourceIdentifierKey
from id_minter.resolvers.data_api_resolver import DataApiIdResolver

SID = SourceIdentifierKey("Work", "folio", "AC-1001")
PRED = SourceIdentifierKey("Work", "sierra", "b1001")


class _StubResolver(DataApiIdResolver):
    """Skips the AWS clients and serves lookups from an in-memory registry."""

    def __init__(self, registry: dict[SourceIdentifierKey, str]) -> None:
        self.registry = registry

    def lookup_ids(
        self, source_ids: list[SourceIdentifierKey]
    ) -> dict[SourceIdentifierKey, str]:
        return {k: self.registry[k] for k in source_ids if k in self.registry}


def test_returns_stored_id_when_predecessor_matches() -> None:
    resolver = _StubResolver({SID: "shared01", PRED: "shared01"})
    assert resolver.mint_ids([(SID, PRED)]) == {SID: "shared01"}


def test_returns_stored_id_when_predecessor_unregistered() -> None:
    resolver = _StubResolver({SID: "own00001"})
    assert resolver.mint_ids([(SID, PRED)]) == {SID: "own00001"}


def test_raises_when_predecessor_has_another_canonical_id() -> None:
    resolver = _StubResolver({SID: "fresh001", PRED: "legacy01"})
    with pytest.raises(
        ValueError,
        match=(
            "Predecessor mismatch for Work/folio/AC-1001: registered as fresh001, "
            "but predecessor Work/sierra/b1001 is legacy01"
        ),
    ):
        resolver.mint_ids([(SID, PRED)])


def test_still_refuses_to_mint_unregistered_source_ids() -> None:
    resolver = _StubResolver({PRED: "legacy01"})
    with pytest.raises(NotImplementedError, match="cannot mint new IDs"):
        resolver.mint_ids([(SID, PRED)])


def test_lookup_keys_rows_by_requested_value() -> None:
    """A stored case variant comes back under the casing that was asked for."""
    resolver = DataApiIdResolver.__new__(DataApiIdResolver)
    resolver._table_name = "identifiers"
    calls: list[tuple[str, list[dict]]] = []

    def execute(sql: str, parameters: list[dict]) -> list[dict[str, str]]:
        calls.append((sql, parameters))
        return [{"RequestedId": "B1001", "CanonicalId": "case0001"}]

    resolver._execute = execute  # type: ignore[method-assign]
    requested = SourceIdentifierKey("Work", "sierra", "B1001")
    other = SourceIdentifierKey("Work", "sierra", "b1002")

    assert resolver.lookup_ids([requested, other]) == {requested: "case0001"}

    sql, parameters = calls[0]
    assert "FROM (VALUES ROW(:sid0), ROW(:sid1)) AS r(req)" in sql
    assert {p["name"]: p["value"]["stringValue"] for p in parameters} == {
        "sid0": "B1001",
        "sid1": "b1002",
        "ont": "Work",
        "sys": "sierra",
    }


def test_lookup_reads_aliased_columns_from_data_api_labels() -> None:
    """The Data API reports an alias as the column label, not its name."""
    resolver = DataApiIdResolver.__new__(DataApiIdResolver)
    resolver._table_name = "identifiers"
    resolver._cluster_arn = resolver._secret_arn = resolver._database = "test"

    class _RdsData:
        def execute_statement(self, **_: object) -> dict:
            return {
                "columnMetadata": [
                    {"name": "req", "label": "RequestedId"},
                    {"name": "CanonicalId", "label": "CanonicalId"},
                ],
                "records": [[{"stringValue": "B1001"}, {"stringValue": "case0001"}]],
            }

    resolver._rds_data = _RdsData()
    requested = SourceIdentifierKey("Work", "sierra", "B1001")

    assert resolver.lookup_ids([requested]) == {requested: "case0001"}
