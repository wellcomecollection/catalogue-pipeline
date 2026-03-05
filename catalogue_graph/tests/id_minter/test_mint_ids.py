"""
Tests for IDMinter.lookup_ids() and IDMinter.mint_ids().

Derived from RFC 083 (Stable identifiers following mass record migration),
specifically the "Batch minting flow" section.  See test_mint_ids.md for
the full test-case catalogue.

Each test uses a real MySQL 8 database via Docker (see conftest.py).
"""

from __future__ import annotations

import pymysql
import pymysql.connections
import pymysql.cursors
import pytest

from id_minter.mint_ids import IDMinter
from id_minter.models.identifiers import SourceId

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _seed_free_ids(conn: pymysql.connections.Connection, ids: list[str]) -> None:
    """Insert canonical IDs into the pool with Status='free'."""
    cursor = conn.cursor()
    for cid in ids:
        cursor.execute(
            "INSERT INTO canonical_ids (CanonicalId, Status) VALUES (%s, 'free')",
            (cid,),
        )
    conn.commit()


def _seed_identifier(
    conn: pymysql.connections.Connection,
    source_id: SourceId,
    canonical_id: str,
) -> None:
    """Insert a pre-existing source→canonical mapping.

    Also ensures the canonical ID row exists in canonical_ids (as 'assigned').
    """
    cursor = conn.cursor()
    cursor.execute(
        "INSERT IGNORE INTO canonical_ids (CanonicalId, Status) VALUES (%s, 'assigned')",
        (canonical_id,),
    )
    cursor.execute(
        "INSERT INTO identifiers (OntologyType, SourceSystem, SourceId, CanonicalId) "
        "VALUES (%s, %s, %s, %s)",
        (*source_id, canonical_id),
    )
    conn.commit()


def _open_second_connection() -> pymysql.connections.Connection:
    """Open an independent connection (simulates a concurrent process)."""
    return pymysql.connect(
        host="localhost",
        port=3306,
        user="id_minter",
        password="id_minter",
        database="identifiers",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


def _get_identifier_row(
    conn: pymysql.connections.Connection,
    source_id: SourceId,
) -> dict[str, str] | None:
    """Read back a single identifiers row by composite PK."""
    cursor = conn.cursor()
    cursor.execute(
        "SELECT OntologyType, SourceSystem, SourceId, CanonicalId "
        "FROM identifiers "
        "WHERE OntologyType = %s AND SourceSystem = %s AND SourceId = %s",
        source_id,
    )
    result: dict[str, str] | None = cursor.fetchone()
    return result


def _get_canonical_status(
    conn: pymysql.connections.Connection, canonical_id: str
) -> str:
    """Return the Status of a canonical_ids row."""
    cursor = conn.cursor()
    cursor.execute(
        "SELECT Status FROM canonical_ids WHERE CanonicalId = %s",
        (canonical_id,),
    )
    row: dict[str, str] | None = cursor.fetchone()
    assert row is not None, f"canonical_ids row not found for {canonical_id}"
    return row["Status"]


def _count_identifier_rows(conn: pymysql.connections.Connection) -> int:
    """Return the total number of rows in the identifiers table."""
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM identifiers")
    return int(cursor.fetchone()["COUNT(*)"])


def _count_assigned_ids(conn: pymysql.connections.Connection) -> int:
    """Return the number of canonical IDs with Status='assigned'."""
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM canonical_ids WHERE Status = 'assigned'")
    return int(cursor.fetchone()["COUNT(*)"])


# ---------------------------------------------------------------------------
# 1–5  lookup_ids
# ---------------------------------------------------------------------------


class TestLookupIds:
    """Tests for IDMinter.lookup_ids()."""

    def test_returns_existing_mappings(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#1: All source IDs found in DB are returned."""
        entries = [(("Work", "sierra", f"b{i}"), f"canon{i:03d}") for i in range(1, 6)]
        for sid, cid in entries:
            _seed_identifier(ids_db, sid, cid)

        result = IDMinter(ids_db).lookup_ids([sid for sid, _ in entries])

        assert len(result) == 5
        for sid, cid in entries:
            assert result[sid] == cid

    def test_returns_only_found_ids(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#2: Partial match — missing IDs excluded from result."""
        existing: SourceId = ("Work", "sierra", "b1234")
        missing: SourceId = ("Work", "sierra", "b9999")
        _seed_identifier(ids_db, existing, "found001")

        result = IDMinter(ids_db).lookup_ids([existing, missing])
        assert result == {existing: "found001"}

    def test_returns_empty_when_no_matches(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#3: None of the requested source IDs exist."""
        result = IDMinter(ids_db).lookup_ids([("Work", "sierra", "b99999")])
        assert result == {}

    def test_returns_empty_for_empty_input(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#4: No source IDs provided → empty dict."""
        assert IDMinter(ids_db).lookup_ids([]) == {}

    def test_handles_mixed_ontology_types(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#5: Work and Image source IDs in the same batch."""
        work_sid: SourceId = ("Work", "sierra", "b1111")
        image_sid: SourceId = ("Image", "mets", "img-001")
        _seed_identifier(ids_db, work_sid, "wk000001")
        _seed_identifier(ids_db, image_sid, "im000001")

        result = IDMinter(ids_db).lookup_ids([work_sid, image_sid])
        assert result == {work_sid: "wk000001", image_sid: "im000001"}


# ---------------------------------------------------------------------------
# 6–8  mint_ids — happy paths
# ---------------------------------------------------------------------------


class TestMintNewIds:
    """Tests for the basic mint/lookup paths."""

    def test_empty_input_returns_empty(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        assert IDMinter(ids_db).mint_ids([]) == {}

    def test_all_existing_returns_without_claiming(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#6: All source IDs already exist → no free IDs consumed."""
        entries = [(("Work", "sierra", f"b{i}"), f"exist{i:03d}") for i in range(1, 4)]
        for sid, cid in entries:
            _seed_identifier(ids_db, sid, cid)
        _seed_free_ids(ids_db, ["spare001", "spare002"])

        result = IDMinter(ids_db).mint_ids([(sid, None) for sid, _ in entries])

        for sid, cid in entries:
            assert result[sid] == cid
        # Free pool untouched
        assert _get_canonical_status(ids_db, "spare001") == "free"
        assert _get_canonical_status(ids_db, "spare002") == "free"

    def test_all_new_claims_from_pool(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#7: All source IDs are new → claims free IDs, inserts mappings."""
        free = [f"newid{i:03d}" for i in range(1, 4)]
        _seed_free_ids(ids_db, free)
        sids: list[SourceId] = [("Work", "sierra", f"b{i}") for i in range(1, 4)]

        result = IDMinter(ids_db).mint_ids([(s, None) for s in sids])

        assert set(result.values()) == set(free)
        for s in sids:
            row = _get_identifier_row(ids_db, s)
            assert row is not None
            assert row["CanonicalId"] == result[s]
        for cid in free:
            assert _get_canonical_status(ids_db, cid) == "assigned"

    def test_mixed_existing_and_new(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#8: Reuses existing, claims only for new."""
        existing_sid: SourceId = ("Work", "sierra", "b0001")
        _seed_identifier(ids_db, existing_sid, "existaaa")
        _seed_free_ids(ids_db, ["newbbb01"])

        new_sid: SourceId = ("Work", "sierra", "b0002")
        result = IDMinter(ids_db).mint_ids([(existing_sid, None), (new_sid, None)])

        assert result[existing_sid] == "existaaa"
        assert result[new_sid] == "newbbb01"


# ---------------------------------------------------------------------------
# 9–13  mint_ids — predecessor inheritance
# ---------------------------------------------------------------------------


class TestPredecessorInheritance:
    """Tests for the predecessor → canonical ID inheritance path."""

    def test_inherits_predecessor_canonical_id(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#9: Predecessor found, new source ID not found → inherits."""
        pred: SourceId = ("Work", "sierra", "b1234")
        _seed_identifier(ids_db, pred, "legacy01")

        new_sid: SourceId = ("Work", "axiell", "AC-5678")
        result = IDMinter(ids_db).mint_ids([(new_sid, pred)])

        assert result[new_sid] == "legacy01"
        row = _get_identifier_row(ids_db, new_sid)
        assert row is not None
        assert row["CanonicalId"] == "legacy01"

    def test_multiple_predecessors_in_batch(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#11: Each new source ID inherits from its own predecessor."""
        pred_a: SourceId = ("Work", "sierra", "b1111")
        pred_b: SourceId = ("Work", "sierra", "b2222")
        _seed_identifier(ids_db, pred_a, "legacyaa")
        _seed_identifier(ids_db, pred_b, "legacybb")

        new_a: SourceId = ("Work", "axiell", "AC-1111")
        new_b: SourceId = ("Work", "axiell", "AC-2222")

        result = IDMinter(ids_db).mint_ids([(new_a, pred_a), (new_b, pred_b)])

        assert result[new_a] == "legacyaa"
        assert result[new_b] == "legacybb"

    def test_cross_type_predecessor(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#12: Image source ID inherits from a Work predecessor."""
        pred: SourceId = ("Work", "sierra", "b3333")
        _seed_identifier(ids_db, pred, "cross001")

        new_sid: SourceId = ("Image", "axiell-image", "AI-3333")
        result = IDMinter(ids_db).mint_ids([(new_sid, pred)])

        assert result[new_sid] == "cross001"

    def test_reprocessing_without_predecessor(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#13: Source ID already has canonical ID from a prior predecessor
        mint — re-processing without predecessor returns the same ID.
        """
        pred: SourceId = ("Work", "sierra", "b4444")
        _seed_identifier(ids_db, pred, "stable01")

        new_sid: SourceId = ("Work", "axiell", "AC-4444")
        # First mint with predecessor
        IDMinter(ids_db).mint_ids([(new_sid, pred)])

        # Second mint without predecessor — lookup path returns existing
        result = IDMinter(ids_db).mint_ids([(new_sid, None)])
        assert result[new_sid] == "stable01"


# ---------------------------------------------------------------------------
# 14–15  mint_ids — idempotency
# ---------------------------------------------------------------------------


class TestIdempotency:
    """Idempotent behaviour for repeated / duplicate requests."""

    def test_identical_request_twice(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#14: Same result both times, no extra IDs claimed."""
        _seed_free_ids(ids_db, ["idem0001", "idem0002"])
        sid: SourceId = ("Work", "sierra", "b7000")

        minter = IDMinter(ids_db)
        first = minter.mint_ids([(sid, None)])
        second = minter.mint_ids([(sid, None)])

        assert first == second
        # Only one free ID should have been consumed
        assert _count_assigned_ids(ids_db) == 1

    def test_duplicate_source_ids_in_batch(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#15: Deduplicated — single canonical ID per unique source ID."""
        _seed_free_ids(ids_db, ["dup00001", "dup00002", "dup00003", "dup00004"])

        sid_a: SourceId = ("Work", "sierra", "b8001")
        sid_b: SourceId = ("Work", "sierra", "b8002")

        result = IDMinter(ids_db).mint_ids(
            [(sid_a, None), (sid_b, None), (sid_a, None), (sid_b, None)]
        )

        assert len(result) == 2
        assert result[sid_a] != result[sid_b]


# ---------------------------------------------------------------------------
# 16–18  mint_ids — error cases
# ---------------------------------------------------------------------------


class TestErrorCases:
    """Error paths that should raise and commit nothing."""

    def test_missing_predecessor_raises(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#16: Predecessor not found → ValueError, nothing committed."""
        new_sid: SourceId = ("Work", "axiell", "AC-9999")
        missing_pred: SourceId = ("Work", "sierra", "b0000")

        with pytest.raises(ValueError, match="Predecessor not found"):
            IDMinter(ids_db).mint_ids([(new_sid, missing_pred)])

        assert _count_identifier_rows(ids_db) == 0

    def test_pool_fully_exhausted_raises(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#17: Zero free IDs available → RuntimeError."""
        sids: list[SourceId] = [("Work", "sierra", f"b{i}") for i in range(1, 4)]

        with pytest.raises(RuntimeError, match="Free ID pool exhausted"):
            IDMinter(ids_db).mint_ids([(s, None) for s in sids])

    def test_pool_partially_exhausted_raises(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#18: Fewer free IDs than needed → RuntimeError, nothing committed."""
        _seed_free_ids(ids_db, ["short001"])
        sids: list[SourceId] = [("Work", "sierra", f"b{i}") for i in range(1, 4)]

        with pytest.raises(RuntimeError, match="Free ID pool exhausted"):
            IDMinter(ids_db).mint_ids([(s, None) for s in sids])

        assert _count_identifier_rows(ids_db) == 0


# ---------------------------------------------------------------------------
# 19–21  mint_ids — race conditions
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
        """#19: Another process inserts the same source ID between our
        lookup and our INSERT.  FOR SHARE re-read discovers the winner's
        canonical ID.
        """
        sid: SourceId = ("Work", "sierra", "b5555")
        _seed_free_ids(ids_db, ["loser001"])

        original_lookup = IDMinter.lookup_ids

        def lookup_then_race(
            self_: IDMinter, source_ids: list[SourceId]
        ) -> dict[SourceId, str]:
            result = original_lookup(self_, source_ids)
            # Simulate the winner inserting via a separate connection
            winner = _open_second_connection()
            try:
                _seed_identifier(winner, sid, "winner01")
            finally:
                winner.close()
            return result

        monkeypatch.setattr(IDMinter, "lookup_ids", lookup_then_race)

        result = IDMinter(ids_db).mint_ids([(sid, None)])
        assert result[sid] == "winner01"

    def test_race_loser_returns_unused_id_to_pool(
        self,
        ids_db: pymysql.connections.Connection,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """#20: The free ID claimed by the loser stays 'free'."""
        sid: SourceId = ("Work", "sierra", "b6666")
        _seed_free_ids(ids_db, ["unused01"])

        original_lookup = IDMinter.lookup_ids

        def lookup_then_race(
            self_: IDMinter, source_ids: list[SourceId]
        ) -> dict[SourceId, str]:
            result = original_lookup(self_, source_ids)
            winner = _open_second_connection()
            try:
                _seed_identifier(winner, sid, "winner02")
            finally:
                winner.close()
            return result

        monkeypatch.setattr(IDMinter, "lookup_ids", lookup_then_race)

        result = IDMinter(ids_db).mint_ids([(sid, None)])
        assert result[sid] == "winner02"
        assert _get_canonical_status(ids_db, "unused01") == "free"

    def test_race_partial_wins(
        self,
        ids_db: pymysql.connections.Connection,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """#21: Batch of 3 new source IDs — one is stolen by a concurrent
        process.  Only the 2 IDs we actually won are marked 'assigned';
        the third stays 'free'.
        """
        won_a: SourceId = ("Work", "sierra", "b7001")
        won_b: SourceId = ("Work", "sierra", "b7002")
        lost: SourceId = ("Work", "sierra", "b7003")

        _seed_free_ids(ids_db, ["pool0001", "pool0002", "pool0003"])

        original_lookup = IDMinter.lookup_ids

        def lookup_then_race(
            self_: IDMinter, source_ids: list[SourceId]
        ) -> dict[SourceId, str]:
            result = original_lookup(self_, source_ids)
            # Concurrent winner steals only `lost`
            winner = _open_second_connection()
            try:
                _seed_identifier(winner, lost, "stolen01")
            finally:
                winner.close()
            return result

        monkeypatch.setattr(IDMinter, "lookup_ids", lookup_then_race)

        result = IDMinter(ids_db).mint_ids([(won_a, None), (won_b, None), (lost, None)])

        # The lost source ID got the winner's canonical ID
        assert result[lost] == "stolen01"
        # The other two got IDs from the pool
        assert result[won_a] in {"pool0001", "pool0002", "pool0003"}
        assert result[won_b] in {"pool0001", "pool0002", "pool0003"}
        assert result[won_a] != result[won_b]

        # Only the two IDs we actually used are marked 'assigned'
        for cid in [result[won_a], result[won_b]]:
            assert _get_canonical_status(ids_db, cid) == "assigned"

        # The third pool ID was claimed but unused — must still be 'free'
        all_pool = {"pool0001", "pool0002", "pool0003"}
        used_pool = {result[won_a], result[won_b]}
        unused = (all_pool - used_pool).pop()
        assert _get_canonical_status(ids_db, unused) == "free"


# ---------------------------------------------------------------------------
# 22–23  mint_ids — transaction atomicity
# ---------------------------------------------------------------------------


class TestTransactionAtomicity:
    """Verify that failures roll back the entire transaction."""

    def test_predecessor_error_rolls_back_prior_inserts(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#22: A predecessor error mid-batch means nothing is committed,
        even for source IDs processed before the error.
        """
        _seed_free_ids(ids_db, ["atom0001"])

        ok_sid: SourceId = ("Work", "sierra", "b9001")
        bad_sid: SourceId = ("Work", "axiell", "AC-9002")
        missing_pred: SourceId = ("Work", "sierra", "b0000")

        with pytest.raises(ValueError, match="Predecessor not found"):
            IDMinter(ids_db).mint_ids([(ok_sid, None), (bad_sid, missing_pred)])

        # Neither the good nor the bad source ID should be in the DB
        assert _get_identifier_row(ids_db, ok_sid) is None
        assert _get_identifier_row(ids_db, bad_sid) is None

    def test_pool_exhaustion_rolls_back_prior_inserts(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#23: Pool exhaustion after predecessor inserts → everything
        rolled back, including the predecessor-inherited mappings.
        Claimed free IDs stay 'free' because the transaction is rolled back.
        """
        pred: SourceId = ("Work", "sierra", "b1234")
        _seed_identifier(ids_db, pred, "legacy01")

        inherited_sid: SourceId = ("Work", "axiell", "AC-1234")
        new_a: SourceId = ("Work", "sierra", "b9010")
        new_b: SourceId = ("Work", "sierra", "b9011")

        # Only 1 free ID but need 2 for the new source IDs
        _seed_free_ids(ids_db, ["short001"])

        with pytest.raises(RuntimeError, match="Free ID pool exhausted"):
            IDMinter(ids_db).mint_ids(
                [(inherited_sid, pred), (new_a, None), (new_b, None)]
            )

        # The predecessor-inherited mapping should not exist
        assert _get_identifier_row(ids_db, inherited_sid) is None
        # The free ID should still be free
        assert _get_canonical_status(ids_db, "short001") == "free"


# ---------------------------------------------------------------------------
# 24–25  mint_ids — pool management
# ---------------------------------------------------------------------------


class TestPoolManagement:
    """Verify correct Status transitions in the canonical_ids table."""

    def test_used_ids_marked_assigned(
        self, ids_db: pymysql.connections.Connection
    ) -> None:
        """#24: After a successful mint, claimed IDs are 'assigned'."""
        _seed_free_ids(ids_db, ["pool0001", "pool0002"])
        sids: list[SourceId] = [
            ("Work", "sierra", "b6001"),
            ("Work", "sierra", "b6002"),
        ]

        result = IDMinter(ids_db).mint_ids([(s, None) for s in sids])

        for cid in result.values():
            assert _get_canonical_status(ids_db, cid) == "assigned"

    def test_only_used_ids_marked_assigned(
        self,
        ids_db: pymysql.connections.Connection,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """#25: Batch claims 3 free IDs from pool, but a race means only
        2 are used.  The unused one stays 'free'.
        """
        sid_a: SourceId = ("Work", "sierra", "b6101")
        sid_b: SourceId = ("Work", "sierra", "b6102")
        stolen: SourceId = ("Work", "sierra", "b6103")

        _seed_free_ids(ids_db, ["mgmt0001", "mgmt0002", "mgmt0003"])

        original_lookup = IDMinter.lookup_ids

        def lookup_then_race(
            self_: IDMinter, source_ids: list[SourceId]
        ) -> dict[SourceId, str]:
            result = original_lookup(self_, source_ids)
            winner = _open_second_connection()
            try:
                _seed_identifier(winner, stolen, "thief001")
            finally:
                winner.close()
            return result

        monkeypatch.setattr(IDMinter, "lookup_ids", lookup_then_race)

        result = IDMinter(ids_db).mint_ids(
            [(sid_a, None), (sid_b, None), (stolen, None)]
        )

        # Two pool IDs used, one not
        used = {result[sid_a], result[sid_b]}
        unused = ({"mgmt0001", "mgmt0002", "mgmt0003"} - used).pop()

        for cid in used:
            assert _get_canonical_status(ids_db, cid) == "assigned"
        assert _get_canonical_status(ids_db, unused) == "free"
