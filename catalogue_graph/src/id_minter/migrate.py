"""Migrate identifiers data from a Parquet export into the new two-table schema.

Reads Parquet files exported from the old `identifiers-serverless` database and
inserts the data into the new schema (RFC 083):
  - canonical_ids: CanonicalId, Status, CreatedAt
  - identifiers:   OntologyType, SourceSystem, SourceId, CanonicalId, CreatedAt

Uses LOAD DATA LOCAL INFILE for fast bulk loading.

Usage from a notebook or script:

    from id_minter.migrate import migrate_from_parquet
    result = migrate_from_parquet(conn, Path("./data/identifiers"))
"""

from __future__ import annotations

import csv
import logging
import tempfile
from pathlib import Path

import polars as pl
from pydantic import BaseModel

from id_minter.database import DBConnection

logger = logging.getLogger(__name__)


class MigrationResult(BaseModel):
    canonical_ids_inserted: int
    identifiers_inserted: int
    total_source_rows: int


def truncate_tables(conn: DBConnection) -> None:
    """Truncate the identifiers and canonical_ids tables (order matters for FK)."""
    cursor = conn.cursor()
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    cursor.execute("TRUNCATE TABLE identifiers")
    cursor.execute("TRUNCATE TABLE canonical_ids")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    conn.commit()
    logger.info("Truncated identifiers and canonical_ids tables")


DEFAULT_BATCH_SIZE = 1_000_000


def migrate_from_parquet(
    conn: DBConnection,
    parquet_dir: Path,
    *,
    batch_size: int = DEFAULT_BATCH_SIZE,
    truncate: bool = False,
) -> MigrationResult:
    """Read parquet files and bulk-load into the new schema via LOAD DATA LOCAL INFILE.

    Args:
        conn: An open database connection with local_infile=True.
        parquet_dir: Directory containing .parquet files from the RDS export.
        batch_size: Number of rows per LOAD DATA batch (for progress reporting).
        truncate: If True, truncate both tables before inserting.

    Returns:
        MigrationResult with counts of rows processed.
    """
    df = pl.read_parquet(parquet_dir / "*.parquet")
    total_rows = len(df)
    logger.info(f"Loaded {total_rows:,} rows from {parquet_dir}")

    if truncate:
        truncate_tables(conn)

    cursor = conn.cursor()
    cursor.execute("SET unique_checks = 0")
    cursor.execute("SET foreign_key_checks = 0")

    canonical_ids_inserted = _load_canonical_ids(conn, df, batch_size)
    identifiers_inserted = _load_identifiers(conn, df, batch_size)

    cursor.execute("SET unique_checks = 1")
    cursor.execute("SET foreign_key_checks = 1")
    conn.commit()

    return MigrationResult(
        canonical_ids_inserted=canonical_ids_inserted,
        identifiers_inserted=identifiers_inserted,
        total_source_rows=total_rows,
    )


def _load_canonical_ids(conn: DBConnection, df: pl.DataFrame, batch_size: int) -> int:
    """Write unique CanonicalIds to a TSV and bulk-load into canonical_ids in batches."""
    unique_ids = df["CanonicalId"].unique().to_list()
    total = len(unique_ids)
    logger.info(f"Loading {total:,} unique canonical IDs via LOAD DATA LOCAL INFILE")

    loaded = 0
    for start in range(0, total, batch_size):
        batch = unique_ids[start : start + batch_size]

        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".tsv", delete=False, newline=""
        ) as f:
            writer = csv.writer(f, delimiter="\t", lineterminator="\n")
            for cid in batch:
                writer.writerow([cid, "assigned"])
            tsv_path = f.name

        try:
            cursor = conn.cursor()
            cursor.execute(
                "LOAD DATA LOCAL INFILE %s IGNORE INTO TABLE canonical_ids "
                "FIELDS TERMINATED BY '\\t' LINES TERMINATED BY '\\n' "
                "(CanonicalId, Status)",
                (tsv_path,),
            )
            batch_loaded: int = cursor.rowcount or 0
            loaded += batch_loaded
            conn.commit()
        finally:
            Path(tsv_path).unlink(missing_ok=True)

        logger.info(
            f"  canonical_ids progress: {min(start + batch_size, total):,}/{total:,}"
        )

    logger.info(f"Loaded {loaded:,} canonical IDs ({total - loaded:,} already existed)")
    return loaded


def _load_identifiers(conn: DBConnection, df: pl.DataFrame, batch_size: int) -> int:
    """Write identifier rows to a TSV and bulk-load into identifiers in batches."""
    total = len(df)
    logger.info(f"Loading {total:,} identifier rows via LOAD DATA LOCAL INFILE")

    cols = ["OntologyType", "SourceSystem", "SourceId", "CanonicalId"]
    loaded = 0
    for start in range(0, total, batch_size):
        batch_df = df.slice(start, batch_size)

        with tempfile.NamedTemporaryFile(suffix=".tsv", delete=False) as f:
            tsv_path = f.name
        batch_df.select(cols).write_csv(tsv_path, separator="\t", include_header=False)

        try:
            cursor = conn.cursor()
            cursor.execute(
                "LOAD DATA LOCAL INFILE %s IGNORE INTO TABLE identifiers "
                "FIELDS TERMINATED BY '\\t' LINES TERMINATED BY '\\n' "
                "(OntologyType, SourceSystem, SourceId, CanonicalId)",
                (tsv_path,),
            )
            batch_loaded: int = cursor.rowcount or 0
            loaded += batch_loaded
            conn.commit()
        finally:
            Path(tsv_path).unlink(missing_ok=True)

        logger.info(
            f"  identifiers progress: {min(start + batch_size, total):,}/{total:,}"
        )

    logger.info(f"Loaded {loaded:,} identifiers ({total - loaded:,} already existed)")
    return loaded
