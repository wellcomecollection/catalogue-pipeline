"""Tests for DataApiIdResolver, with its lookup or Data API client stubbed so no AWS is needed."""

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


class _StubRdsData:
    """Answers execute_statement in the Data API's response shape and records calls."""

    def __init__(self, column_metadata: list[dict], records: list[list[dict]]) -> None:
        self.response = {"columnMetadata": column_metadata, "records": records}
        self.calls: list[dict] = []

    def execute_statement(self, **kwargs: object) -> dict:
        self.calls.append(kwargs)
        return self.response


def _resolver_with_data_api(rds_data: _StubRdsData) -> DataApiIdResolver:
    resolver = DataApiIdResolver.__new__(DataApiIdResolver)
    resolver._cluster_arn = resolver._secret_arn = resolver._database = "test"
    resolver._table_name = "identifiers"
    resolver._rds_data = rds_data
    return resolver


def test_lookup_keys_rows_by_requested_value() -> None:
    """A stored case variant comes back under the casing that was asked for."""
    rds_data = _StubRdsData(
        # The Data API gives an alias as the label; a column without one falls back to its name.
        column_metadata=[
            {"name": "req", "label": "RequestedId"},
            {"name": "CanonicalId"},
        ],
        records=[[{"stringValue": "B1001"}, {"stringValue": "case0001"}]],
    )
    resolver = _resolver_with_data_api(rds_data)
    requested = SourceIdentifierKey("Work", "sierra", "B1001")
    other = SourceIdentifierKey("Work", "sierra", "b1002")

    assert resolver.lookup_ids([requested, other]) == {requested: "case0001"}

    call = rds_data.calls[0]
    assert "FROM (VALUES ROW(:sid0), ROW(:sid1)) AS r(req)" in str(call["sql"])
    assert {p["name"]: p["value"]["stringValue"] for p in call["parameters"]} == {
        "sid0": "B1001",
        "sid1": "b1002",
        "ont": "Work",
        "sys": "sierra",
    }
