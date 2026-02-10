from typing import Any

import pytest
import sqlite3


@pytest.fixture(scope="function")
def sqlite_canonical_ids_db(tmp_path) -> sqlite3.Connection:
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
