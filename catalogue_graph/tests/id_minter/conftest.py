from typing import Any

import sqlite3
import pytest
from unittest import mock


@pytest.fixture(scope="function")
def ids_db(tmp_path) -> sqlite3.Connection:
    db_path = tmp_path / "test.db"
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE canonical_ids (
            CanonicalId VARCHAR(8) NOT NULL PRIMARY KEY,
            Status TEXT NOT NULL DEFAULT 'free' CHECK(Status IN ('free', 'assigned')),
            CreatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    cursor.execute('''
        CREATE INDEX idx_free ON canonical_ids (Status, CanonicalId)
    ''')
    conn.commit()
    yield conn
    conn.close()


@pytest.fixture(scope="function")
def mock_generate_ids(request) -> None:
    with mock.patch("id_minter.identifiers.generate_ids", side_effect=lambda count: request.param[count]):
        yield


@pytest.fixture(scope="function")
def free_ids() -> list[str]:
    return [
        f"aaaaaaa{i}" for i in range(2, 5)
    ]


@pytest.fixture(scope="function")
def assigned_ids() -> list[str]:
    return [
        f"bbbbbbb{i}" for i in range(2, 5)
    ]
