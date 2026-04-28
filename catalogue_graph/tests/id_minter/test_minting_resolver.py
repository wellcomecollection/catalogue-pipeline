"""
Tests for MintingResolver.lookup_ids() and MintingResolver.mint_ids().

Derived from RFC 083 (Stable identifiers following mass record migration),
specifically the "Batch minting flow" section.

Each test uses a real MySQL 8 database via Docker (see conftest.py).

Test categories:
- lookup_ids: batch lookup of existing canonical IDs
- mint_ids happy paths: new ID claiming, mixed existing/new
- Predecessor inheritance: canonical ID inheritance during migrations
- Idempotency: repeated and duplicate requests
- Error cases: missing predecessors, pool exhaustion
- Race conditions: concurrent minter scenarios (monkeypatched)
- Transaction atomicity: rollback on failure
- Pool management: free/assigned status transitions
"""

from __future__ import annotations

import pymysql
import pymysql.connections
import pymysql.cursors
import pytest

from id_minter.models.identifier import SourceIdentifierKey
from id_minter.resolvers.minting_resolver import MintingResolver
from tests.id_minter.conftest import (
    count_assigned_ids,
    count_identifier_rows,
    get_canonical_status,
    get_identifier_row,
    open_second_connection,
    seed_free_ids,
    seed_identifier,
)

# ---------------------------------------------------------------------------
# lookup_ids
# ---------------------------------------------------------------------------


class TestLookupIds:
    """Tests for MintingResolver.lookup_ids()."""

    def test_returns_existing_mappings(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """All source IDs found in DB are returned."""
        identifiers = [
            (SourceIdentifierKey("Work", "sierra", f"b{i}"), f"canon{i:03d}")
            for i in range(1, 6)
        ]
        for sid, cid in identifiers:
            seed_identifier(ids_db, sid, cid)

        result = MintingResolver.from_connection(ids_db).lookup_ids(
            [sid for sid, _ in identifiers]
        )

        assert len(result) == 5
        for sid, cid in identifiers:
            assert result[sid] == cid

    def test_returns_only_found_ids(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Partial match — missing IDs excluded from result."""
        existing: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b1234")
        missing: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b9999")
        seed_identifier(ids_db, existing, "found001")

        result = MintingResolver.from_connection(ids_db).lookup_ids([existing, missing])
        assert result == {SourceIdentifierKey("Work", "sierra", "b1234"): "found001"}

    def test_returns_empty_when_no_matches(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """None of the requested source IDs exist."""
        result = MintingResolver.from_connection(ids_db).lookup_ids(
            [SourceIdentifierKey("Work", "sierra", "b99999")]
        )
        assert result == {}

    def test_returns_empty_for_empty_input(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """No source IDs provided → empty dict."""
        assert MintingResolver.from_connection(ids_db).lookup_ids([]) == {}

    def test_handles_mixed_ontology_types(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Work and Image source IDs in the same batch."""
        work_sid: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b1111")
        image_sid: SourceIdentifierKey = SourceIdentifierKey("Image", "mets", "img-001")
        seed_identifier(ids_db, work_sid, "wk000001")
        seed_identifier(ids_db, image_sid, "im000001")

        result = MintingResolver.from_connection(ids_db).lookup_ids(
            [work_sid, image_sid]
        )
        assert result == {work_sid: "wk000001", image_sid: "im000001"}


# ---------------------------------------------------------------------------
# mint_ids — happy paths
# ---------------------------------------------------------------------------


class TestMintNewIds:
    """Tests for the basic mint/lookup paths."""

    def test_empty_input_returns_empty(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """No source IDs provided → empty dict."""
        assert MintingResolver.from_connection(ids_db).mint_ids([]) == {}

    def test_all_existing_returns_without_claiming(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """All source IDs already exist → no free IDs consumed."""
        identifiers = [
            (SourceIdentifierKey("Work", "folio", f"b{i}"), f"exist{i:03d}")
            for i in range(1, 4)
        ]
        for sid, cid in identifiers:
            seed_identifier(ids_db, sid, cid)
        seed_free_ids(ids_db, ["spare001", "spare002"])

        result = MintingResolver.from_connection(ids_db).mint_ids(
            [(sid, None) for sid, _ in identifiers]
        )

        for sid, cid in identifiers:
            assert result[sid] == cid
        # Free pool untouched
        assert get_canonical_status(ids_db, "spare001") == "free"
        assert get_canonical_status(ids_db, "spare002") == "free"

    def test_all_new_claims_from_pool(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """All source IDs are new with no predecessors → claims free IDs, inserts mappings."""
        free = [f"newid{i:03d}" for i in range(1, 4)]
        seed_free_ids(ids_db, free)
        sids: list[SourceIdentifierKey] = [
            SourceIdentifierKey("Work", "folio", f"b{i}") for i in range(1, 4)
        ]

        result = MintingResolver.from_connection(ids_db).mint_ids(
            [(s, None) for s in sids]
        )

        assert set(result.values()) == set(free)
        for s in sids:
            row = get_identifier_row(ids_db, s)
            assert row is not None
            assert row["CanonicalId"] == result[s]
        for cid in free:
            assert get_canonical_status(ids_db, cid) == "assigned"

    def test_mixed_existing_and_new(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Existing IDs returned as-is; only new IDs claim from pool."""
        existing_sid: SourceIdentifierKey = SourceIdentifierKey(
            "Work", "axiell", "b0001"
        )
        seed_identifier(ids_db, existing_sid, "existaaa")
        seed_free_ids(ids_db, ["newbbb01"])

        new_sid: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b0002")
        result = MintingResolver.from_connection(ids_db).mint_ids(
            [(existing_sid, None), (new_sid, None)]
        )

        assert result[existing_sid] == "existaaa"
        assert result[new_sid] == "newbbb01"


# ---------------------------------------------------------------------------
# mint_ids — predecessor inheritance
# ---------------------------------------------------------------------------


class TestPredecessorInheritance:
    """Tests for the predecessor → canonical ID inheritance path."""

    def test_inherits_predecessor_canonical_id(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """New source ID inherits predecessor's canonical ID."""
        pred: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b1234")
        seed_identifier(ids_db, pred, "legacy01")

        new_sid: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "AC-5678")
        result = MintingResolver.from_connection(ids_db).mint_ids([(new_sid, pred)])

        assert result[new_sid] == "legacy01"
        row = get_identifier_row(ids_db, new_sid)
        assert row is not None
        assert row["CanonicalId"] == "legacy01"

    def test_multiple_predecessors_in_batch(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Each new source ID inherits from its own predecessor."""
        pred_a: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b1111")
        pred_b: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b2222")
        seed_identifier(ids_db, pred_a, "legacyaa")
        seed_identifier(ids_db, pred_b, "legacybb")

        new_a: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "AC-1111")
        new_b: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "AC-2222")

        result = MintingResolver.from_connection(ids_db).mint_ids(
            [(new_a, pred_a), (new_b, pred_b)]
        )

        assert result[new_a] == "legacyaa"
        assert result[new_b] == "legacybb"

    def test_reprocessing_without_predecessor(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Source ID already has canonical ID from a prior predecessor
        mint — re-processing without predecessor returns the same ID.
        """
        pred: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b4444")
        seed_identifier(ids_db, pred, "stable01")

        new_sid: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "AC-4444")
        # First mint with predecessor
        MintingResolver.from_connection(ids_db).mint_ids([(new_sid, pred)])

        # Second mint without predecessor — lookup path returns existing
        result = MintingResolver.from_connection(ids_db).mint_ids([(new_sid, None)])
        assert result[new_sid] == "stable01"


# ---------------------------------------------------------------------------
# mint_ids — idempotency
# ---------------------------------------------------------------------------


class TestIdempotency:
    """Idempotent behaviour for repeated / duplicate requests."""

    def test_identical_request_twice(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Same result both times, no extra IDs claimed."""
        seed_free_ids(ids_db, ["idem0001", "idem0002"])
        sid: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b7000")

        minter = MintingResolver.from_connection(ids_db)
        first = minter.mint_ids([(sid, None)])
        second = minter.mint_ids([(sid, None)])

        assert first == second
        # Only one free ID should have been consumed
        assert count_assigned_ids(ids_db) == 1

    def test_duplicate_source_ids_in_batch(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Deduplicated — single canonical ID per unique source ID."""
        seed_free_ids(ids_db, ["dup00001", "dup00002", "dup00003", "dup00004"])

        sid_a: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b8001")
        sid_b: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b8002")

        result = MintingResolver.from_connection(ids_db).mint_ids(
            [(sid_a, None), (sid_b, None), (sid_a, None), (sid_b, None)]
        )

        assert len(result) == 2
        assert result[sid_a] != result[sid_b]
        # Only 2 of the 4 pool IDs should have been claimed
        assert count_assigned_ids(ids_db) == 2


# ---------------------------------------------------------------------------
# mint_ids — error cases
# ---------------------------------------------------------------------------


class TestErrorCases:
    """Error paths that should raise and commit nothing."""

    def test_missing_predecessor_raises(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Predecessor not found → ValueError, nothing committed."""
        new_sid: SourceIdentifierKey = SourceIdentifierKey("Work", "axiell", "AC-9999")
        missing_pred: SourceIdentifierKey = SourceIdentifierKey(
            "Work", "sierra", "b0000"
        )

        with pytest.raises(ValueError, match="Predecessor not found"):
            MintingResolver.from_connection(ids_db).mint_ids([(new_sid, missing_pred)])

        assert count_identifier_rows(ids_db) == 0

    def test_pool_fully_exhausted_raises(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Zero free IDs available → RuntimeError."""
        sids: list[SourceIdentifierKey] = [
            SourceIdentifierKey("Work", "axiell", f"b{i}") for i in range(1, 4)
        ]

        with pytest.raises(RuntimeError, match="Free ID pool exhausted"):
            MintingResolver.from_connection(ids_db).mint_ids([(s, None) for s in sids])

    def test_pool_partially_exhausted_raises(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Fewer free IDs than needed → RuntimeError, nothing committed."""
        seed_free_ids(ids_db, ["short001"])
        sids: list[SourceIdentifierKey] = [
            SourceIdentifierKey("Work", "folio", f"b{i}") for i in range(1, 4)
        ]

        with pytest.raises(RuntimeError, match="Free ID pool exhausted"):
            MintingResolver.from_connection(ids_db).mint_ids([(s, None) for s in sids])

        assert count_identifier_rows(ids_db) == 0

    def test_conflicting_predecessors_raise(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Same source id with two different predecessors → ValueError, nothing committed."""
        sid = SourceIdentifierKey("Item", "sierra", "i9999")
        pred_a = SourceIdentifierKey("Item", "sierra", "i0000")
        pred_b = SourceIdentifierKey("Item", "sierra", "i0001")

        with pytest.raises(ValueError, match="Conflicting predecessors"):
            MintingResolver.from_connection(ids_db).mint_ids(
                [(sid, pred_a), (sid, pred_b)]
            )

        assert count_identifier_rows(ids_db) == 0

    def test_duplicate_request_with_same_predecessor_is_allowed(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """Same (source_id, predecessor) repeated is fine — only conflicting predecessors raise."""
        pred = SourceIdentifierKey("Work", "sierra", "b1234")
        seed_identifier(ids_db, pred, "legacy01")

        new_sid = SourceIdentifierKey("Work", "folio", "AC-1234")
        result = MintingResolver.from_connection(ids_db).mint_ids(
            [(new_sid, pred), (new_sid, pred)]
        )
        assert result[new_sid] == "legacy01"

    def test_same_source_id_with_and_without_predecessor_raises(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """A None predecessor and a real predecessor for the same source id is a conflict."""
        sid = SourceIdentifierKey("Item", "sierra", "i9999")
        pred = SourceIdentifierKey("Item", "sierra", "i0000")

        with pytest.raises(ValueError, match="Conflicting predecessors"):
            MintingResolver.from_connection(ids_db).mint_ids([(sid, None), (sid, pred)])

        assert count_identifier_rows(ids_db) == 0


# ---------------------------------------------------------------------------
# mint_ids — race conditions
#
# We monkeypatch lookup_ids to return empty, then have a second connection
# insert the winner's mapping *between* our lookup and our INSERT.
# This exercises the ON DUPLICATE KEY UPDATE + FOR SHARE re-read path.
# ---------------------------------------------------------------------------


class TestRaceConditions:
    """Concurrent-minter scenarios using a second MySQL connection."""

    def test_race_returns_winner_id(
        self,
        ids_db: pymysql.connections.Connection,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """Another process inserts the same source ID between our
        lookup and our INSERT.  FOR SHARE re-read discovers the winner's
        canonical ID.
        """
        sid: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b5555")
        seed_free_ids(ids_db, ["loser001"])

        original_lookup = MintingResolver.lookup_ids

        def lookup_then_race(
            self_: MintingResolver, source_ids: list[SourceIdentifierKey]
        ) -> dict[SourceIdentifierKey, str]:
            result = original_lookup(self_, source_ids)
            # Simulate the winner inserting via a separate connection
            winner = open_second_connection()
            try:
                seed_identifier(winner, sid, "winner01")
            finally:
                winner.close()
            return result

        monkeypatch.setattr(MintingResolver, "lookup_ids", lookup_then_race)

        result = MintingResolver.from_connection(ids_db).mint_ids([(sid, None)])
        assert result[sid] == "winner01"

    def test_race_loser_returns_unused_id_to_pool(
        self,
        ids_db: pymysql.connections.Connection,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """The free ID claimed by the race loser stays 'free'."""
        sid: SourceIdentifierKey = SourceIdentifierKey("Work", "axiell", "b6666")
        seed_free_ids(ids_db, ["unused01"])

        original_lookup = MintingResolver.lookup_ids

        def lookup_then_race(
            self_: MintingResolver, source_ids: list[SourceIdentifierKey]
        ) -> dict[SourceIdentifierKey, str]:
            result = original_lookup(self_, source_ids)
            winner = open_second_connection()
            try:
                seed_identifier(winner, sid, "winner02")
            finally:
                winner.close()
            return result

        monkeypatch.setattr(MintingResolver, "lookup_ids", lookup_then_race)

        result = MintingResolver.from_connection(ids_db).mint_ids([(sid, None)])
        assert result[sid] == "winner02"
        assert get_canonical_status(ids_db, "unused01") == "free"

    def test_race_partial_wins(
        self,
        ids_db: pymysql.connections.Connection,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """Batch of 3 new source IDs — one is stolen by a concurrent
        process.  Only the 2 IDs we actually won are marked 'assigned';
        the third stays 'free'.
        """
        won_a: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b7001")
        won_b: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b7002")
        lost: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b7003")

        seed_free_ids(ids_db, ["pool0001", "pool0002", "pool0003"])

        original_lookup = MintingResolver.lookup_ids

        def lookup_then_race(
            self_: MintingResolver, source_ids: list[SourceIdentifierKey]
        ) -> dict[SourceIdentifierKey, str]:
            result = original_lookup(self_, source_ids)
            # Concurrent winner steals only `lost`
            winner = open_second_connection()
            try:
                seed_identifier(winner, lost, "stolen01")
            finally:
                winner.close()
            return result

        monkeypatch.setattr(MintingResolver, "lookup_ids", lookup_then_race)

        result = MintingResolver.from_connection(ids_db).mint_ids(
            [(won_a, None), (won_b, None), (lost, None)]
        )

        # The lost source ID got the winner's canonical ID
        assert result[lost] == "stolen01"
        # The other two got IDs from the pool
        assert result[won_a] in {"pool0001", "pool0002", "pool0003"}
        assert result[won_b] in {"pool0001", "pool0002", "pool0003"}
        assert result[won_a] != result[won_b]

        # Only the two IDs we actually used are marked 'assigned'
        for cid in [result[won_a], result[won_b]]:
            assert get_canonical_status(ids_db, cid) == "assigned"

        # The third pool ID was claimed but unused — must still be 'free'
        all_pool = {"pool0001", "pool0002", "pool0003"}
        used_pool = {result[won_a], result[won_b]}
        unused = (all_pool - used_pool).pop()
        assert get_canonical_status(ids_db, unused) == "free"


# ---------------------------------------------------------------------------
# mint_ids — transaction atomicity
# ---------------------------------------------------------------------------


class TestTransactionAtomicity:
    """Verify that failures roll back the entire transaction."""

    def test_predecessor_error_rolls_back_prior_inserts(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """A predecessor error mid-batch means nothing is committed,
        even for source IDs processed before the error.
        """
        seed_free_ids(ids_db, ["atom0001"])

        ok_sid: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b9001")
        bad_sid: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "AC-9002")
        missing_pred: SourceIdentifierKey = SourceIdentifierKey(
            "Work", "folio", "b0000"
        )

        with pytest.raises(ValueError, match="Predecessor not found"):
            MintingResolver.from_connection(ids_db).mint_ids(
                [(ok_sid, None), (bad_sid, missing_pred)]
            )

        # Neither the good nor the bad source ID should be in the DB
        assert get_identifier_row(ids_db, ok_sid) is None
        assert get_identifier_row(ids_db, bad_sid) is None

    def test_pool_exhaustion_rolls_back_with_predecessor(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """A successor inherits a predecessor's canonical ID (inserting
        a new identifiers row), but then pool exhaustion occurs for other
        source IDs in the batch.  The entire transaction is rolled back,
        including the successor's inherited mapping.
        """
        pred: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b1234")
        seed_identifier(ids_db, pred, "legacy01")

        successor: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "AC-1234")
        new_a: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b9010")
        new_b: SourceIdentifierKey = SourceIdentifierKey("Work", "folio", "b9011")

        # Only 1 free ID but need 2 for the new source IDs
        seed_free_ids(ids_db, ["short001"])

        with pytest.raises(RuntimeError, match="Free ID pool exhausted"):
            MintingResolver.from_connection(ids_db).mint_ids(
                [(successor, pred), (new_a, None), (new_b, None)]
            )

        # The new identifiers rows should not exist — rolled back
        assert get_identifier_row(ids_db, successor) is None
        assert get_identifier_row(ids_db, new_a) is None
        assert get_identifier_row(ids_db, new_b) is None

        # The free ID should still be free
        assert get_canonical_status(ids_db, "short001") == "free"
        # The predecessor's pre-existing mapping is untouched
        assert get_identifier_row(ids_db, pred) is not None
        assert get_canonical_status(ids_db, "legacy01") == "assigned"


# ---------------------------------------------------------------------------
# mint_ids — pool management
# ---------------------------------------------------------------------------


class TestPoolManagement:
    """Verify correct Status transitions in the canonical_ids table."""

    def test_used_ids_marked_assigned(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """After a successful mint, claimed IDs are 'assigned'."""
        seed_free_ids(ids_db, ["pool0001", "pool0002"])
        sids: list[SourceIdentifierKey] = [
            SourceIdentifierKey("Work", "sierra", "b6001"),
            SourceIdentifierKey("Work", "sierra", "b6002"),
        ]

        result = MintingResolver.from_connection(ids_db).mint_ids(
            [(s, None) for s in sids]
        )

        for cid in result.values():
            assert get_canonical_status(ids_db, cid) == "assigned"

    def test_only_used_ids_marked_assigned(
        self,
        ids_db: pymysql.connections.Connection,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """Batch claims 3 free IDs from pool, but a race means only
        2 are used.  The unused one stays 'free'.
        """
        sid_a: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b6101")
        sid_b: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b6102")
        stolen: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b6103")

        seed_free_ids(ids_db, ["mgmt0001", "mgmt0002", "mgmt0003"])

        original_lookup = MintingResolver.lookup_ids

        def lookup_then_race(
            self_: MintingResolver, source_ids: list[SourceIdentifierKey]
        ) -> dict[SourceIdentifierKey, str]:
            result = original_lookup(self_, source_ids)
            winner = open_second_connection()
            try:
                seed_identifier(winner, stolen, "thief001")
            finally:
                winner.close()
            return result

        monkeypatch.setattr(MintingResolver, "lookup_ids", lookup_then_race)

        result = MintingResolver.from_connection(ids_db).mint_ids(
            [(sid_a, None), (sid_b, None), (stolen, None)]
        )

        # Two pool IDs used, one not
        used = {result[sid_a], result[sid_b]}
        unused = ({"mgmt0001", "mgmt0002", "mgmt0003"} - used).pop()

        for cid in used:
            assert get_canonical_status(ids_db, cid) == "assigned"
        assert get_canonical_status(ids_db, unused) == "free"


# ---------------------------------------------------------------------------
# mint_ids — round-trip batching
#
# Verify that the per-row INSERT loops have been collapsed into single
# multi-row INSERTs. We count INSERT INTO identifiers statements issued
# against the DB cursor for a representative batch.
# ---------------------------------------------------------------------------


class TestRoundTripBatching:
    """Assert that INSERTs are coalesced into a single statement per branch."""

    def _count_insert_statements(
        self,
        ids_db: pymysql.connections.Connection,
        requests: list[tuple[SourceIdentifierKey, SourceIdentifierKey | None]],
    ) -> int:
        cursor_factory = ids_db.cursor
        insert_count = 0

        def counting_cursor() -> object:
            real_cursor = cursor_factory()
            real_execute = real_cursor.execute

            def counting_execute(query: str, args: object = None) -> object:
                nonlocal insert_count
                if "INSERT INTO identifiers" in query:
                    insert_count += 1
                return real_execute(query, args)

            real_cursor.execute = counting_execute
            return real_cursor

        ids_db.cursor = counting_cursor
        try:
            MintingResolver.from_connection(ids_db).mint_ids(requests)
        finally:
            ids_db.cursor = cursor_factory
        return insert_count

    def test_new_ids_use_single_multi_row_insert(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """A batch of N new source IDs should issue exactly ONE INSERT."""
        seed_free_ids(ids_db, [f"new{i:05d}" for i in range(5)])
        requests: list[tuple[SourceIdentifierKey, SourceIdentifierKey | None]] = [
            (SourceIdentifierKey("Work", "folio", f"b{i}"), None) for i in range(5)
        ]

        insert_count = self._count_insert_statements(ids_db, requests)

        assert insert_count == 1
        # All five mappings should still be persisted.
        for sid, _ in requests:
            assert get_identifier_row(ids_db, sid) is not None

    def test_inheritance_uses_single_multi_row_insert(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """A batch of N predecessor-inheriting source IDs → ONE INSERT."""
        predecessors: list[SourceIdentifierKey] = [
            SourceIdentifierKey("Work", "sierra", f"b{i}") for i in range(5)
        ]
        for i, pred in enumerate(predecessors):
            seed_identifier(ids_db, pred, f"legacy{i:02d}")

        requests: list[tuple[SourceIdentifierKey, SourceIdentifierKey | None]] = [
            (SourceIdentifierKey("Work", "folio", f"AC-{i}"), pred)
            for i, pred in enumerate(predecessors)
        ]

        insert_count = self._count_insert_statements(ids_db, requests)

        assert insert_count == 1
        for sid, _ in requests:
            assert get_identifier_row(ids_db, sid) is not None

    def test_mixed_inheritance_and_new_uses_two_inserts(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """One INSERT for the inheritance branch + one for the new-ID branch."""
        pred: SourceIdentifierKey = SourceIdentifierKey("Work", "sierra", "b9000")
        seed_identifier(ids_db, pred, "legacy99")
        seed_free_ids(ids_db, ["mix00001", "mix00002", "mix00003"])

        requests: list[tuple[SourceIdentifierKey, SourceIdentifierKey | None]] = [
            (SourceIdentifierKey("Work", "folio", "AC-9000"), pred),  # inheritance
            (SourceIdentifierKey("Work", "folio", "b9001"), None),  # new
            (SourceIdentifierKey("Work", "folio", "b9002"), None),  # new
            (SourceIdentifierKey("Work", "folio", "b9003"), None),  # new
        ]

        insert_count = self._count_insert_statements(ids_db, requests)

        assert insert_count == 2
