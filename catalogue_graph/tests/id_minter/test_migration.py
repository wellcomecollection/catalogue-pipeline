"""Tests for the migration module (migrate.py) and migration step (steps/migration.py).

Uses the local MySQL docker container (mysql.docker-compose.yml).
"""

from pathlib import Path
from unittest.mock import patch

import polars as pl
import pymysql
import pymysql.cursors
import pytest

from id_minter.migrate import migrate_from_parquet, truncate_tables
from id_minter.models.step_events import MigrationRequest, MigrationResponse
from id_minter.steps.migration import handler, verify_migration

pytestmark = pytest.mark.database

# ---------------------------------------------------------------------------
# Sample data
# ---------------------------------------------------------------------------

SAMPLE_ROWS = [
    {
        "CanonicalId": "abcd1234",
        "OntologyType": "Work",
        "SourceSystem": "sierra-system-number",
        "SourceId": "b1000001",
    },
    {
        "CanonicalId": "abcd1234",
        "OntologyType": "Work",
        "SourceSystem": "mets",
        "SourceId": "b1000001",
    },
    {
        "CanonicalId": "efgh5678",
        "OntologyType": "Work",
        "SourceSystem": "sierra-system-number",
        "SourceId": "b2000002",
    },
    {
        "CanonicalId": "jklm9abc",
        "OntologyType": "Image",
        "SourceSystem": "miro-image-number",
        "SourceId": "V0012345",
    },
]


@pytest.fixture()
def parquet_dir(tmp_path: Path) -> Path:
    """Write sample data as parquet files that mimic an RDS export."""
    df = pl.DataFrame(SAMPLE_ROWS)
    out = tmp_path / "parquet"
    out.mkdir()
    df.write_parquet(out / "part-00000.parquet")
    return out


@pytest.fixture()
def ids_db_local_infile(
    mysql_schema: None,
) -> pymysql.connections.Connection:
    """Per-test connection with local_infile=True for LOAD DATA LOCAL INFILE."""
    conn = pymysql.connect(
        host="localhost",
        port=3306,
        user="id_minter",
        password="id_minter",
        database="identifiers",
        cursorclass=pymysql.cursors.Cursor,
        autocommit=False,
        local_infile=True,
    )
    yield conn
    cursor = conn.cursor()
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    cursor.execute("DELETE FROM identifiers")
    cursor.execute("DELETE FROM canonical_ids")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    conn.commit()
    conn.close()


# ===================================================================
# migrate_from_parquet tests
# ===================================================================


class TestMigrateFromParquet:
    def test_inserts_all_rows(
        self,
        ids_db_local_infile: pymysql.connections.Connection,
        parquet_dir: Path,
    ) -> None:
        result = migrate_from_parquet(ids_db_local_infile, parquet_dir)

        assert result.total_source_rows == 4
        # 3 unique canonical IDs in sample data
        assert result.canonical_ids_inserted == 3
        assert result.identifiers_inserted == 4

    def test_canonical_ids_have_assigned_status(
        self,
        ids_db_local_infile: pymysql.connections.Connection,
        parquet_dir: Path,
    ) -> None:
        migrate_from_parquet(ids_db_local_infile, parquet_dir)

        cursor = ids_db_local_infile.cursor()
        cursor.execute(
            "SELECT CanonicalId, Status FROM canonical_ids ORDER BY CanonicalId"
        )
        rows = cursor.fetchall()

        assert len(rows) == 3
        assert all(status == "assigned" for _, status in rows)
        canonical_ids = {cid for cid, _ in rows}
        assert canonical_ids == {"abcd1234", "efgh5678", "jklm9abc"}

    def test_identifiers_have_correct_fk(
        self,
        ids_db_local_infile: pymysql.connections.Connection,
        parquet_dir: Path,
    ) -> None:
        migrate_from_parquet(ids_db_local_infile, parquet_dir)

        cursor = ids_db_local_infile.cursor()
        cursor.execute(
            "SELECT i.CanonicalId FROM identifiers i "
            "LEFT JOIN canonical_ids c ON i.CanonicalId = c.CanonicalId "
            "WHERE c.CanonicalId IS NULL"
        )
        orphaned = cursor.fetchall()
        assert len(orphaned) == 0

    def test_idempotent_with_ignore(
        self,
        ids_db_local_infile: pymysql.connections.Connection,
        parquet_dir: Path,
    ) -> None:
        """Running migration twice should not fail or duplicate rows (IGNORE keyword)."""
        result1 = migrate_from_parquet(ids_db_local_infile, parquet_dir)
        result2 = migrate_from_parquet(ids_db_local_infile, parquet_dir)

        # Second run inserts 0 new rows because they already exist
        assert result2.canonical_ids_inserted == 0
        assert result2.identifiers_inserted == 0

        # Total in DB should still match the first run
        cursor = ids_db_local_infile.cursor()
        cursor.execute("SELECT COUNT(*) FROM canonical_ids")
        (count,) = cursor.fetchone()
        assert count == result1.canonical_ids_inserted

    def test_truncate_clears_before_insert(
        self,
        ids_db_local_infile: pymysql.connections.Connection,
        parquet_dir: Path,
    ) -> None:
        # First migration
        migrate_from_parquet(ids_db_local_infile, parquet_dir)

        # Second migration with truncate
        result = migrate_from_parquet(ids_db_local_infile, parquet_dir, truncate=True)

        assert result.canonical_ids_inserted == 3
        assert result.identifiers_inserted == 4

    def test_batching_produces_same_result(
        self,
        ids_db_local_infile: pymysql.connections.Connection,
        parquet_dir: Path,
    ) -> None:
        """With a small batch size, results should be identical."""
        result = migrate_from_parquet(ids_db_local_infile, parquet_dir, batch_size=2)

        assert result.total_source_rows == 4
        assert result.canonical_ids_inserted == 3
        assert result.identifiers_inserted == 4


# ===================================================================
# truncate_tables tests
# ===================================================================


class TestTruncateTables:
    def test_truncate_removes_all_data(
        self,
        ids_db_local_infile: pymysql.connections.Connection,
        parquet_dir: Path,
    ) -> None:
        migrate_from_parquet(ids_db_local_infile, parquet_dir)

        truncate_tables(ids_db_local_infile)

        cursor = ids_db_local_infile.cursor()
        cursor.execute("SELECT COUNT(*) FROM canonical_ids")
        (canonical_count,) = cursor.fetchone()
        cursor.execute("SELECT COUNT(*) FROM identifiers")
        (id_count,) = cursor.fetchone()

        assert canonical_count == 0
        assert id_count == 0


# ===================================================================
# verify_migration tests
# ===================================================================


class TestVerifyMigration:
    def test_passes_after_clean_migration(
        self,
        ids_db_local_infile: pymysql.connections.Connection,
        parquet_dir: Path,
    ) -> None:
        migrate_from_parquet(ids_db_local_infile, parquet_dir)

        canonical_count, identifiers_count, orphaned_count = verify_migration(
            ids_db_local_infile
        )

        assert canonical_count == 3
        assert identifiers_count == 4
        assert orphaned_count == 0

    def test_fails_on_orphaned_identifiers(
        self,
        ids_db_local_infile: pymysql.connections.Connection,
        parquet_dir: Path,
    ) -> None:
        migrate_from_parquet(ids_db_local_infile, parquet_dir)

        # Manually delete a canonical_id to create an orphan
        cursor = ids_db_local_infile.cursor()
        cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
        cursor.execute("DELETE FROM canonical_ids WHERE CanonicalId = 'abcd1234'")
        cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
        ids_db_local_infile.commit()

        with pytest.raises(RuntimeError, match="orphaned identifiers"):
            verify_migration(ids_db_local_infile)


# ===================================================================
# handler (full step) tests
# ===================================================================


class TestHandler:
    def test_full_handler_with_mocked_s3(
        self,
        ids_db_local_infile: pymysql.connections.Connection,
        parquet_dir: Path,
    ) -> None:
        """Test the handler end-to-end with S3 download mocked."""
        from id_minter.config import IdMinterConfig, RDSClientConfig

        config = IdMinterConfig(
            rds_client=RDSClientConfig(password="id_minter"),
        )
        request = MigrationRequest(
            export_date="2026-02-26",
            truncate=True,
        )

        with patch(
            "id_minter.steps.migration.download_parquet_from_s3",
            return_value=parquet_dir,
        ):
            response = handler(request, config=config)

        assert isinstance(response, MigrationResponse)
        assert response.total_source_rows == 4
        assert response.canonical_ids_inserted == 3
        assert response.identifiers_inserted == 4
        assert response.canonical_ids_verified == 3
        assert response.identifiers_verified == 4
        assert response.orphaned_identifiers == 0


# ===================================================================
# MigrationRequest model tests
# ===================================================================


class TestMigrationRequest:
    def test_defaults(self) -> None:
        req = MigrationRequest(export_date="2026-02-26")
        assert req.s3_bucket == "wellcomecollection-platform-id-minter"
        assert req.cluster_name == "identifiers-serverless"
        assert req.truncate is True
        assert req.batch_size == 1_000_000

    def test_from_json(self) -> None:
        req = MigrationRequest.model_validate(
            {
                "export_date": "2026-02-26",
                "cluster_name": "identifiers-v2-serverless",
                "truncate": False,
                "batch_size": 500_000,
            }
        )
        assert req.cluster_name == "identifiers-v2-serverless"
        assert req.truncate is False
        assert req.batch_size == 500_000
