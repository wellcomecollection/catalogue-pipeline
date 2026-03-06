"""
Minting IdResolver implementation supporting:
- Batch lookup of existing canonical IDs
- Single-record minting for new IDs
- Predecessor inheritance for migrated records
- Pre-generated ID pool claiming
- Idempotent writes for concurrency
"""

from __future__ import annotations

import structlog

from id_minter.config import DBConfig
from id_minter.database import DBConnection, DBCursor, get_connection
from id_minter.models.identifiers import SourceId

logger = structlog.get_logger(__name__)


class MintingResolver:
    """
    IdResolver that supports predecessor inheritance for stable identifiers
    during source system migrations.
    """

    def __init__(self, config: DBConfig):
        """
        Initialize the MintingResolver.

        Args:
            config: Database configuration for connecting to the identifiers DB.
        """
        self.conn: DBConnection[DBCursor] = get_connection(config)

    @classmethod
    def from_connection(cls, conn: DBConnection[DBCursor]) -> MintingResolver:
        """Create a MintingResolver from an existing database connection.

        Useful for testing where the connection is managed externally.
        """
        resolver = object.__new__(cls)
        resolver.conn = conn
        return resolver

    def __enter__(self) -> MintingResolver:
        return self

    def __exit__(self, *exc: object) -> None:
        self.conn.close()

    def lookup_ids(self, source_ids: list[SourceId]) -> dict[SourceId, str]:
        """
        Batch lookup of canonical IDs for multiple source identifiers.

        This is the optimized path for the common case where records already
        exist in the database. Returns only the IDs that were found — missing
        IDs should be processed via mint_ids().

        Args:
            source_ids: List of (ontology_type, source_system, source_id) tuples

        Returns:
            Dict mapping (ontology_type, source_system, source_id) -> canonical_id
            for all source IDs that were found. Missing IDs are not included.

        Example:
            >>> resolver.lookup_ids([
            ...     ('Work', 'sierra-system-number', 'b1234'),
            ...     ('Image', 'mets-image', 'xyz'),
            ...     ('Work', 'axiell-collections-id', 'new-record')  # doesn't exist
            ... ])
            {
                ('Work', 'sierra-system-number', 'b1234'): 'abc12345',
                ('Image', 'mets-image', 'xyz'): 'def67890'
            }
            # ('Work', 'axiell-collections-id', 'new-record') not in result — needs minting
        """
        if not source_ids:
            return {}

        cursor = self.conn.cursor()

        # Build query with IN clause for batch lookup of mixed ontology types
        # Using tuple comparison: (OntologyType, SourceSystem, SourceId) IN ((?, ?, ?), ...)
        placeholders = ", ".join(["(%s, %s, %s)"] * len(source_ids))
        params = []
        for ontology_type, source_system, source_id in source_ids:
            params.extend([ontology_type, source_system, source_id])

        cursor.execute(
            f"""
            SELECT OntologyType, SourceSystem, SourceId, CanonicalId 
            FROM identifiers 
            WHERE (OntologyType, SourceSystem, SourceId) IN ({placeholders})
        """,
            params,
        )

        results = cursor.fetchall()

        return {
            (row["OntologyType"], row["SourceSystem"], row["SourceId"]): row[
                "CanonicalId"
            ]
            for row in results
        }

    def mint_ids(
        self, requests: list[tuple[SourceId, SourceId | None]]
    ) -> dict[SourceId, str]:
        """
        Batch mint/lookup canonical IDs for multiple source identifiers.

        This is the optimized batch path that minimizes database round-trips:
        1. Batch lookup all source IDs + predecessor IDs (single query)
        2. Fail fast if any predecessors are missing
        3. Batch INSERT for predecessor inheritance cases
        4. Batch claim free IDs from pool (FOR UPDATE SKIP LOCKED)
        5. Batch INSERT for new ID cases
        6. Verify which IDs were actually assigned (race detection)
        7. Mark only used IDs as 'assigned', commit transaction

        All operations occur within a single transaction for atomicity.

        Args:
            requests: List of (source_id, predecessor_or_none) tuples where:
                     - source_id is (ontology_type, source_system, source_id)
                     - predecessor is (ontology_type, source_system, source_id) or None

        Returns:
            Dict mapping source_id -> canonical_id for all inputs

        Raises:
            ValueError: If a predecessor is specified but not found
            RuntimeError: If free ID pool is exhausted

        Example:
            >>> resolver.mint_ids([
            ...     (('Work', 'axiell', 'AC-123'), ('Work', 'sierra', 'b1234')),  # with predecessor
            ...     (('Image', 'mets', 'xyz'), None),  # no predecessor
            ... ])
        """
        # Early return for empty input - no work to do
        if not requests:
            return {}

        try:
            return self._mint_ids_with_rollback(requests)
        except Exception:
            self.conn.rollback()
            raise

    def _mint_ids_with_rollback(
        self,
        requests: list[tuple[SourceId, SourceId | None]],
    ) -> dict[SourceId, str]:
        cursor = self.conn.cursor()
        result: dict[SourceId, str] = {}

        # Build lookup sets
        # -------------------------------------------------------------------------
        # Extract all source IDs from the requests (these are the IDs we want to
        # mint/lookup). Also build a mapping from source_id -> predecessor for
        # records that have a predecessor specified (used for canonical ID inheritance
        # during migrations from one source system to another).
        source_ids = list(dict.fromkeys(req[0] for req in requests))
        predecessors = {req[0]: req[1] for req in requests if req[1] is not None}
        predecessor_ids = list(predecessors.values())

        # Step 1: Batch lookup all source IDs + predecessor IDs together
        # -------------------------------------------------------------------------
        # Single database query to find all existing mappings. We look up both the
        # source IDs (to check if they already have canonical IDs) AND the predecessor
        # IDs (to retrieve the canonical IDs that new records should inherit).
        # Using set() to deduplicate in case any source ID is also listed as a
        # predecessor, avoiding redundant lookups.
        all_ids_to_lookup = list(set(source_ids + predecessor_ids))
        found = self.lookup_ids(all_ids_to_lookup)

        # Populate result with already-existing source IDs
        # -------------------------------------------------------------------------
        # For any source ID that already exists in the database, we're done - just
        # return the existing canonical ID. This is the idempotent "lookup" path.
        for sid in source_ids:
            if sid in found:
                result[sid] = found[sid]
                logger.debug(
                    "Resolved ID",
                    source_id=f"{sid[0]}[{sid[1]}/{sid[2]}]",
                    canonical_id=found[sid],
                    method="looked_up",
                )

        existing_count = len(result)
        missing_count = len(source_ids) - existing_count
        logger.info(
            "Batch lookup complete",
            total=len(source_ids),
            existing=existing_count,
            missing=missing_count,
        )

        # Step 2: Categorize missing source IDs
        # -------------------------------------------------------------------------
        # For source IDs not found in the database, determine which minting strategy:
        #   - needs_inheritance: Has a predecessor -> inherit the predecessor's
        #     canonical ID. This preserves stable identifiers during source system
        #     migrations (e.g., Sierra -> Folio). The new source ID gets the SAME
        #     canonical ID, ensuring external URLs remain valid.
        #   - needs_new_id: No predecessor -> claim a fresh ID from the pre-generated
        #     pool. This is for genuinely new records with no prior identity.
        missing = [sid for sid in source_ids if sid not in found]
        needs_inheritance: list[tuple[SourceId, str]] = []  # (source_id, canonical_id)
        needs_new_id: list[SourceId] = []

        for sid in missing:
            pred = predecessors.get(sid)
            if pred:
                # Fail fast if predecessor doesn't exist - this is a data integrity
                # error. The predecessor record MUST be ingested before any record
                # that references it. This constraint ensures we never accidentally
                # mint a new ID when we should be inheriting an existing one.
                if pred not in found:
                    raise ValueError(
                        f"Predecessor not found: {pred[0]}/{pred[1]}/{pred[2]}"
                    )
                needs_inheritance.append((sid, found[pred]))
            else:
                needs_new_id.append(sid)

        if needs_inheritance or needs_new_id:
            logger.info(
                "Categorized missing IDs",
                needs_inheritance=len(needs_inheritance),
                needs_new_id=len(needs_new_id),
            )

        # Step 3: Batch INSERT for predecessor inheritance
        # -------------------------------------------------------------------------
        # For records inheriting from a predecessor, insert a new row in the
        # identifiers table mapping the new source ID to the predecessor's canonical ID.
        # This creates multiple source IDs pointing to the same canonical ID.
        #
        # ON DUPLICATE KEY UPDATE is used for idempotency - if a concurrent process
        # already inserted this mapping, we don't fail. The "CanonicalId = CanonicalId"
        # is a no-op that prevents the INSERT from failing on duplicates while also
        # not changing any existing data.
        if needs_inheritance:
            for source_key, canonical_id in needs_inheritance:
                ontology_type, source_system, source_id = source_key
                cursor.execute(
                    """
                    INSERT INTO identifiers (OntologyType, SourceSystem, SourceId, CanonicalId)
                    VALUES (%s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE CanonicalId = CanonicalId
                """,
                    (ontology_type, source_system, source_id, canonical_id),
                )
                result[source_key] = canonical_id
                pred = predecessors[source_key]
                logger.debug(
                    "Resolved ID",
                    source_id=f"{source_key[0]}[{source_key[1]}/{source_key[2]}]",
                    canonical_id=canonical_id,
                    method="inherited",
                    predecessor=f"{pred[0]}[{pred[1]}/{pred[2]}]",
                )

            logger.info(
                "Inherited predecessor canonical IDs",
                count=len(needs_inheritance),
                mappings={f"{s[1]}/{s[2]}": cid for s, cid in needs_inheritance},
            )

        # Step 4: Claim free IDs from pool for new records
        # -------------------------------------------------------------------------
        # For genuinely new records, we claim pre-generated canonical IDs from the
        # pool. Using FOR UPDATE SKIP LOCKED is critical for concurrency:
        #   - FOR UPDATE: Locks the selected rows so no other transaction can use them
        #   - SKIP LOCKED: If another transaction has already locked some rows, skip
        #     them and select different free IDs instead. This prevents deadlocks and
        #     allows multiple minters to run concurrently without blocking each other.
        #
        # The IDs are only "claimed" at this point, not yet "assigned" - we haven't
        # confirmed that our INSERTs won the race against concurrent processes.
        if needs_new_id:
            num_needed = len(needs_new_id)
            cursor.execute(
                """
                SELECT CanonicalId FROM canonical_ids 
                WHERE Status = 'free' 
                LIMIT %s
                FOR UPDATE SKIP LOCKED
            """,
                (num_needed,),
            )

            free_ids = [row["CanonicalId"] for row in cursor.fetchall()]
            # Pool exhaustion is a critical error - the pre-generation job should
            # ensure there are always enough free IDs available. If this fails,
            # manual intervention is needed to generate more IDs.
            if len(free_ids) < num_needed:
                raise RuntimeError(
                    f"Free ID pool exhausted - needed {num_needed}, got {len(free_ids)}. "
                    "Trigger pre-generation job and retry."
                )

            # Step 5: Batch INSERT for new IDs
            # ---------------------------------------------------------------------
            # Attempt to insert mappings from each new source ID to its claimed
            # canonical ID. Using ON DUPLICATE KEY UPDATE for idempotency - if a
            # concurrent process already inserted a mapping for this source ID
            # (potentially with a DIFFERENT canonical ID), we don't fail.
            #
            # We track claimed_mapping to remember which canonical ID we TRIED to
            # assign to each source ID - this is needed for race detection in Step 6.
            claimed_mapping: dict[SourceId, str] = {}
            for source_key, canonical_id in zip(needs_new_id, free_ids, strict=True):
                ontology_type, source_system, source_id = source_key
                cursor.execute(
                    """
                    INSERT INTO identifiers (OntologyType, SourceSystem, SourceId, CanonicalId)
                    VALUES (%s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE CanonicalId = CanonicalId
                """,
                    (ontology_type, source_system, source_id, canonical_id),
                )
                claimed_mapping[source_key] = canonical_id

            # Step 6: Verify which IDs were actually assigned (race detection)
            # ---------------------------------------------------------------------
            # Re-read the rows we just tried to INSERT to find the actual canonical
            # IDs. If a concurrent process inserted first, our ON DUPLICATE KEY
            # UPDATE was a no-op and the database contains their ID, not ours. We
            # use FOR SHARE (locking read) rather than a plain SELECT because
            # MySQL's REPEATABLE READ isolation would return the transaction's
            # snapshot, missing rows committed by other transactions since ours
            # started.
            placeholders = ", ".join(["(%s, %s, %s)"] * len(needs_new_id))
            params = []
            for ontology_type, source_system, source_id in needs_new_id:
                params.extend([ontology_type, source_system, source_id])
            cursor.execute(
                f"""
                SELECT OntologyType, SourceSystem, SourceId, CanonicalId
                FROM identifiers
                WHERE (OntologyType, SourceSystem, SourceId) IN ({placeholders})
                FOR SHARE
            """,
                params,
            )
            actual = {
                (row["OntologyType"], row["SourceSystem"], row["SourceId"]): row[
                    "CanonicalId"
                ]
                for row in cursor.fetchall()
            }

            # Step 7: Mark only used IDs as 'assigned'
            # ---------------------------------------------------------------------
            # Only mark canonical IDs as 'assigned' if we actually won the race
            # (i.e., the canonical ID in the database matches what we tried to insert).
            #
            # If we lost the race, the canonical ID we claimed is still 'free' and
            # can be used by another process later - we don't want to mark it as
            # 'assigned' since it wasn't actually used.
            #
            # This ensures the free ID pool isn't depleted by failed race attempts.
            used_canonical_ids = set()
            for source_key in needs_new_id:
                actual_canonical = actual.get(source_key)
                if actual_canonical:
                    result[source_key] = actual_canonical
                    # Only mark as assigned if we won the race (our claimed ID was used)
                    if actual_canonical == claimed_mapping.get(source_key):
                        used_canonical_ids.add(actual_canonical)
                        logger.debug(
                            "Resolved ID",
                            source_id=f"{source_key[0]}[{source_key[1]}/{source_key[2]}]",
                            canonical_id=actual_canonical,
                            method="minted",
                        )
                    else:
                        logger.debug(
                            "Resolved ID",
                            source_id=f"{source_key[0]}[{source_key[1]}/{source_key[2]}]",
                            canonical_id=actual_canonical,
                            method="minted_race_lost",
                            claimed_id=claimed_mapping.get(source_key),
                        )

            # Batch UPDATE to mark all successfully used IDs as 'assigned' in a
            # single query, rather than one UPDATE per ID.
            races_lost = sum(
                1
                for sk in needs_new_id
                if actual.get(sk) and actual[sk] != claimed_mapping.get(sk)
            )
            logger.info(
                "Minted new canonical IDs",
                attempted=len(needs_new_id),
                assigned=len(used_canonical_ids),
                races_lost=races_lost,
            )

            if used_canonical_ids:
                placeholders = ", ".join(["%s"] * len(used_canonical_ids))
                cursor.execute(
                    f"""
                    UPDATE canonical_ids SET Status = 'assigned' 
                    WHERE CanonicalId IN ({placeholders})
                """,
                    list(used_canonical_ids),
                )

        # Commit the transaction - all INSERTs and UPDATEs are atomically applied.
        # If anything fails before this point, the entire transaction is rolled back
        # and no partial changes are persisted.
        self.conn.commit()
        return result
