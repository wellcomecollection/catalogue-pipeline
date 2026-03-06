import subprocess
import time
from collections.abc import Generator
from contextlib import contextmanager
from pathlib import Path
from typing import Any
from unittest import mock
from unittest.mock import patch

import pymysql
import pymysql.cursors
import pytest

from id_minter.models.identifiers import SourceId

CATALOGUE_GRAPH_DIR = Path(__file__).resolve().parent.parent.parent


# ---------------------------------------------------------------------------
# Shared DB helpers
# ---------------------------------------------------------------------------


def seed_free_ids(conn: pymysql.connections.Connection, ids: list[str]) -> None:
    """Insert canonical IDs into the pool with Status='free'."""
    cursor = conn.cursor()
    for cid in ids:
        cursor.execute(
            "INSERT INTO canonical_ids (CanonicalId, Status) VALUES (%s, 'free')",
            (cid,),
        )
    conn.commit()


def seed_identifier(
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


def get_canonical_status(
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


def get_identifier_row(
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


def count_identifier_rows(conn: pymysql.connections.Connection) -> int:
    """Return the total number of rows in the identifiers table."""
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM identifiers")
    return int(cursor.fetchone()["COUNT(*)"])


def count_assigned_ids(conn: pymysql.connections.Connection) -> int:
    """Return the number of canonical IDs with Status='assigned'."""
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM canonical_ids WHERE Status = 'assigned'")
    return int(cursor.fetchone()["COUNT(*)"])


def open_second_connection() -> pymysql.connections.Connection:
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


# ---------------------------------------------------------------------------
# Integration test helpers: ES mock + transformer source stubbing
# ---------------------------------------------------------------------------


def make_source_identifier(
    ontology_type: str = "Work",
    identifier_type: str = "sierra-system-number",
    value: str = "b1000001",
) -> dict:
    return {
        "ontologyType": ontology_type,
        "identifierType": {"id": identifier_type},
        "value": value,
    }


def make_work_doc(
    source_identifier: dict | None = None,
    items: list[dict] | None = None,
) -> dict:
    si = source_identifier or make_source_identifier()
    doc: dict[str, Any] = {
        "state": {"sourceIdentifier": si},
        "data": {"title": "Test Work"},
    }
    if items:
        doc["items"] = items
    return doc


@contextmanager
def stub_transformer_source(docs: list[dict]) -> Generator[None]:
    """Patch IdMintingTransformer.__init__ to use an in-memory source."""
    from core.source import BaseSource
    from id_minter.id_minting_transformer import IdMintingTransformer

    class _StubSource(BaseSource):
        def stream_raw(self) -> Generator[dict]:
            yield from docs

    original_init = IdMintingTransformer.__init__

    def patched_init(self: Any, *args: Any, **kwargs: Any) -> None:
        original_init(self, *args, **kwargs)
        self.source = _StubSource()

    with patch.object(IdMintingTransformer, "__init__", patched_init):
        yield


@pytest.fixture()
def mock_es() -> Generator[None]:
    """Patch get_client and reset MockElasticsearchClient for each test."""
    from tests.mocks import MockElasticsearchClient

    MockElasticsearchClient.reset_mocks()
    with patch(
        "id_minter.steps.id_minter.get_client",
        return_value=MockElasticsearchClient({}, ""),
    ):
        yield


# ---------------------------------------------------------------------------
# Fixtures: MySQL container & schema
# ---------------------------------------------------------------------------
import pymysql.cursors
import pytest

from id_minter.models.identifiers import SourceId

CATALOGUE_GRAPH_DIR = Path(__file__).resolve().parent.parent.parent


# ---------------------------------------------------------------------------
# Shared DB helpers
# ---------------------------------------------------------------------------


def seed_free_ids(conn: pymysql.connections.Connection, ids: list[str]) -> None:
    """Insert canonical IDs into the pool with Status='free'."""
    cursor = conn.cursor()
    for cid in ids:
        cursor.execute(
            "INSERT INTO canonical_ids (CanonicalId, Status) VALUES (%s, 'free')",
            (cid,),
        )
    conn.commit()


def seed_identifier(
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


def get_canonical_status(
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


def get_identifier_row(
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


def count_identifier_rows(conn: pymysql.connections.Connection) -> int:
    """Return the total number of rows in the identifiers table."""
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM identifiers")
    return int(cursor.fetchone()["COUNT(*)"])


def count_assigned_ids(conn: pymysql.connections.Connection) -> int:
    """Return the number of canonical IDs with Status='assigned'."""
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM canonical_ids WHERE Status = 'assigned'")
    return int(cursor.fetchone()["COUNT(*)"])


def open_second_connection() -> pymysql.connections.Connection:
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


# ---------------------------------------------------------------------------
# Integration test helpers: ES mock + transformer source stubbing
# ---------------------------------------------------------------------------


def make_source_identifier(
    ontology_type: str = "Work",
    identifier_type: str = "sierra-system-number",
    value: str = "b1000001",
) -> dict:
    return {
        "ontologyType": ontology_type,
        "identifierType": {"id": identifier_type},
        "value": value,
    }


def make_work_doc(
    source_identifier: dict | None = None,
    items: list[dict] | None = None,
) -> dict:
    si = source_identifier or make_source_identifier()
    doc: dict[str, Any] = {
        "state": {"sourceIdentifier": si},
        "data": {"title": "Test Work"},
    }
    if items:
        doc["items"] = items
    return doc


@contextmanager
def stub_transformer_source(docs: list[dict]) -> Generator[None]:
    """Patch IdMintingTransformer.__init__ to use an in-memory source."""
    from core.source import BaseSource
    from id_minter.id_minting_transformer import IdMintingTransformer

    class _StubSource(BaseSource):
        def stream_raw(self) -> Generator[dict]:
            yield from docs

    original_init = IdMintingTransformer.__init__

    def patched_init(self: Any, *args: Any, **kwargs: Any) -> None:
        original_init(self, *args, **kwargs)
        self.source = _StubSource()

    with patch.object(IdMintingTransformer, "__init__", patched_init):
        yield


@pytest.fixture()
def mock_es() -> Generator[None]:
    """Patch get_client and reset MockElasticsearchClient for each test."""
    from tests.mocks import MockElasticsearchClient

    MockElasticsearchClient.reset_mocks()
    with patch(
        "id_minter.steps.id_minter.get_client",
        return_value=MockElasticsearchClient({}, ""),
    ):
        yield


# ---------------------------------------------------------------------------
# Fixtures: MySQL container & schema
# ---------------------------------------------------------------------------


@pytest.fixture(scope="session")
def mysql_container() -> Generator[None]:
    """Start MySQL via docker compose for the test session."""
    subprocess.run(
        ["docker", "compose", "-f", "mysql.docker-compose.yml", "up", "-d"],
        cwd=str(CATALOGUE_GRAPH_DIR),
        check=True,
    )
    for _ in range(30):
        try:
            conn = pymysql.connect(
                host="localhost",
                port=3306,
                user="id_minter",
                password="id_minter",
                database="identifiers",
            )
            conn.close()
            break
        except pymysql.err.OperationalError:
            time.sleep(1)
    else:
        raise RuntimeError("MySQL container did not become ready in time")
    yield


@pytest.fixture(scope="session")
def mysql_schema(mysql_container: None) -> Generator[None]:
    """Apply migrations to create the schema."""
    from id_minter.config import IdMinterConfig, RDSClientConfig
    from id_minter.database import apply_migrations

    config = IdMinterConfig(
        rds_client=RDSClientConfig(password="id_minter"),
    )
    apply_migrations(config)
    yield


@pytest.fixture(scope="function")
def ids_db(mysql_schema: None) -> Generator[pymysql.connections.Connection]:
    """Per-test connection that truncates tables on teardown."""
    conn = pymysql.connect(
        host="localhost",
        port=3306,
        user="id_minter",
        password="id_minter",
        database="identifiers",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
        local_infile=True,
    )
    yield conn
    cursor = conn.cursor()
    cursor.execute("DELETE FROM identifiers")
    cursor.execute("DELETE FROM canonical_ids")
    conn.commit()
    conn.close()


@pytest.fixture(scope="function")
def mock_generate_ids(request: pytest.FixtureRequest) -> Generator[mock.Mock]:
    # the request parameter should be a dict mapping from the count of ids to generate, to a list of ids to return.
    # the lambda looks up the count in the dict and, in order to more closely mimic the
    # real behaviour of the generate_ids function, returns a generator that yields the corresponding ids.
    with mock.patch(
        "id_minter.identifiers.generate_ids",
        side_effect=lambda count: (i for i in request.param[count]),
    ) as id_generator:
        yield id_generator


@pytest.fixture(scope="function")
def free_ids() -> list[str]:
    return [f"aaaaaaa{i}" for i in range(2, 5)]


@pytest.fixture(scope="function")
def assigned_ids() -> list[str]:
    return [f"bbbbbbb{i}" for i in range(2, 5)]
