from typing import Any

import sqlite3
import pytest
from unittest import mock


@pytest.fixture(scope="function")
def ids_db(tmp_path) -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
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
    # the request parameter should be a dict mapping from the count of ids to generate, to a list of ids to return.
    # the lambda looks up the count in the dict and, in order to more closely mimic the
    # real behaviour of the generate_ids function, returns a generator that yields the corresponding ids.
    with mock.patch("id_minter.identifiers.generate_ids", side_effect=lambda count: (i for i in request.param[count])):
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
