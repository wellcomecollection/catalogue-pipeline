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
