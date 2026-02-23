import subprocess
import time
from collections.abc import Generator
from pathlib import Path
from unittest import mock

import pymysql
import pymysql.cursors
import pytest

CATALOGUE_GRAPH_DIR = Path(__file__).resolve().parent.parent.parent


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
        cursorclass=pymysql.cursors.Cursor,
        autocommit=False,
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
