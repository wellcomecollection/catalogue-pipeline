"""Lookup-only IdResolver backed by the identifiers MySQL table.

This resolver can look up existing source→canonical ID mappings but does
**not** mint new IDs.  It is intended as a temporary implementation for
verifying the id_minter pipeline before full minting support is added.
"""

from __future__ import annotations

from collections import defaultdict

import structlog

from id_minter.config import IdMinterConfig
from id_minter.database import DBConnection, get_connection
from id_minter.models.identifier import SourceId

logger = structlog.get_logger(__name__)

_BATCH_SIZE = 500


class LookupOnlyIdResolver:
    """IdResolver that queries MySQL for existing mappings.

    Satisfies the ``IdResolver`` Protocol (lookup_ids + mint_ids).
    ``mint_ids`` deliberately raises so callers that require minting
    fail fast until a full resolver is implemented.
    """

    def __init__(self, config: IdMinterConfig) -> None:
        self._config = config

    def _get_connection(self) -> DBConnection:
        return get_connection(self._config)

    def lookup_ids(self, source_ids: list[SourceId]) -> dict[SourceId, str]:
        if not source_ids:
            return {}

        # Group by (OntologyType, SourceSystem) to minimise round-trips
        batches: dict[tuple[str, str], list[str]] = defaultdict(list)
        for ont, sys, val in source_ids:
            batches[(ont, sys)].append(val)

        result: dict[SourceId, str] = {}
        conn = self._get_connection()
        try:
            cursor = conn.cursor()
            for (ontology_type, source_system), values in batches.items():
                for i in range(0, len(values), _BATCH_SIZE):
                    chunk = values[i : i + _BATCH_SIZE]
                    placeholders = ", ".join(["%s"] * len(chunk))
                    query = (
                        f"SELECT OntologyType, SourceSystem, SourceId, CanonicalId "
                        f"FROM `{self._config.db_table}` "
                        f"WHERE OntologyType = %s AND SourceSystem = %s "
                        f"AND SourceId IN ({placeholders})"
                    )
                    params = [ontology_type, source_system, *chunk]
                    cursor.execute(query, params)
                    for row in cursor:
                        key: SourceId = (
                            row["OntologyType"],
                            row["SourceSystem"],
                            row["SourceId"],
                        )
                        result[key] = row["CanonicalId"]
        finally:
            conn.close()

        logger.info(
            "Looked up identifiers",
            requested=len(source_ids),
            found=len(result),
        )
        return result

    def mint_ids(
        self, requests: list[tuple[SourceId, SourceId | None]]
    ) -> dict[SourceId, str]:
        source_ids = [src for src, _ in requests]
        result = self.lookup_ids(source_ids)
        missing = set(source_ids) - set(result)
        if missing:
            raise NotImplementedError(
                f"LookupOnlyIdResolver cannot mint new IDs. "
                f"{len(missing)} identifier(s) not found in the database."
            )
        return result
