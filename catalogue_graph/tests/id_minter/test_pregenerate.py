from sqlite3 import Connection
from unittest.mock import Mock

import pytest

from id_minter.pregenerate import ShortfallError, get_free_id_count, top_up_ids


def preload_ids(
    ids_db: Connection, free_ids: list[str], assigned_ids: list[str]
) -> None:
    """
    Set up the database with a given set of free and assigned ids.
    """
    cursor = ids_db.cursor()
    cursor.executemany(
        """
        INSERT INTO canonical_ids (CanonicalId, Status) VALUES (?, ?)
        """,
        [(id, "free") for id in free_ids] + [(id, "assigned") for id in assigned_ids],
    )
    ids_db.commit()


def assert_table_looks_like(
    ids_db: Connection, rows: list[tuple[str, str]], where_clause: str = ""
) -> None:
    cursor = ids_db.cursor()
    # ignore createdAt, as this allows us to define exact expectations at compile time,
    # without having to worry about the dynamic nature of createdAt values.
    cursor.execute(
        f"""
        SELECT CanonicalId, Status FROM canonical_ids ORDER BY CanonicalId {where_clause}
        """
    )
    actual_rows = cursor.fetchall()
    # this doesn't have to be a separate assertion,
    # but it's nice to have a clear error message if the number of rows is wrong,
    # rather than just a mismatch in the contents of the rows.
    assert len(actual_rows) == len(rows)
    assert actual_rows == sorted(rows)


def test_adequate_free_ids_does_nothing(
    ids_db: Connection, free_ids: list[str], assigned_ids: list[str]
) -> None:
    preload_ids(ids_db, free_ids, assigned_ids)
    cursor = ids_db.cursor()

    cursor.execute("SELECT * FROM canonical_ids ORDER BY CanonicalId")
    before = cursor.fetchall()

    top_up_ids(ids_db, 3)

    cursor.execute("SELECT * FROM canonical_ids ORDER BY CanonicalId")
    after = cursor.fetchall()

    assert before == after


@pytest.mark.parametrize(
    "mock_generate_ids", [{2: ["vssjb422", "yp8psp82"]}], indirect=["mock_generate_ids"]
)
def test_inadequate_free_ids_generates_more(
    ids_db: Connection,
    mock_generate_ids: Mock,
    free_ids: list[str],
    assigned_ids: list[str],
) -> None:
    preload_ids(ids_db, free_ids, assigned_ids)
    assert get_free_id_count(ids_db) < 5
    top_up_ids(ids_db, 5)
    assert get_free_id_count(ids_db) == 5

    assert_table_looks_like(
        ids_db,
        [(id, "free") for id in free_ids + ["vssjb422", "yp8psp82"]]
        + [(id, "assigned") for id in assigned_ids],
    )


@pytest.mark.parametrize(
    "mock_generate_ids",
    [
        {
            5: [
                "aaaaaaa3",  # this id clashes with an existing free id
                "aaaaaaa4",  # this id clashes with an existing free id
                "bbbbbbb4",  # this id clashes with an existing assigned id
                "aaaaaaa6",  # this id is new and should be added to the database
                "aaaaaaa7",  # this id is new and should be added to the database
            ],
            3: ["njn9f485", "vssjb422", "yp8psp82"],  # All new ids
        }
    ],
    indirect=["mock_generate_ids"],
)
def test_clashes(
    ids_db: Connection,
    mock_generate_ids: Mock,
    free_ids: list[str],
    assigned_ids: list[str],
) -> None:
    preload_ids(ids_db, free_ids, assigned_ids)

    assert get_free_id_count(ids_db) < 8
    top_up_ids(ids_db, 8)
    assert get_free_id_count(ids_db) == 8
    assert_table_looks_like(
        ids_db,
        [
            (id, "free")
            for id in free_ids
            + ["aaaaaaa6", "aaaaaaa7", "njn9f485", "vssjb422", "yp8psp82"]
        ]
        + [(id, "assigned") for id in assigned_ids],
    )


@pytest.mark.parametrize(
    "mock_generate_ids",
    [
        {
            5: [
                "aaaaaaa3",  # this id clashes with an existing free id
                "aaaaaaa4",  # this id clashes with an existing free id
                "bbbbbbb4",  # this id clashes with an existing assigned id
                "aaaaaaa6",  # this id is new and should be added to the database
                "aaaaaaa7",  # this id is new and should be added to the database
            ],
            3: ["aaaaaaa3", "vssjb422", "yp8psp82"],  # Only two of these are new
        }
    ],
    indirect=["mock_generate_ids"],
)
def test_persistent_clashes(
    ids_db: Connection,
    mock_generate_ids: Mock,
    free_ids: list[str],
    assigned_ids: list[str],
) -> None:
    """
    top_up_ids makes two attempts to top up to the desired count of free ids.
    It is possible that the second attempt could also generate clashing ids.
    Any non-clashing ids should still be added to the database, and the function should
    stop after the second attempt regardless of whether it has successfully topped up to the desired count,
    to avoid an infinite loop of attempts.

    Responsibility for monitoring this and manually intervening if there are repeated clashes is outside the scope
    of this function.

    The intent is to maintain a pretty large pool of free ids at all times, so that even if we do get some clashes,
    and do not keep the pool topped up to the desired count, we should still have enough free ids
    for normal operations.
    """
    preload_ids(ids_db, free_ids, assigned_ids)
    assert get_free_id_count(ids_db) < 8
    with pytest.raises(ShortfallError):
        top_up_ids(ids_db, 8)
    assert get_free_id_count(ids_db) == 7
    assert_table_looks_like(
        ids_db,
        [
            (id, "free")
            for id in free_ids + ["aaaaaaa6", "aaaaaaa7", "vssjb422", "yp8psp82"]
        ]
        + [(id, "assigned") for id in assigned_ids],
    )


def test_created_at(ids_db: Connection) -> None:
    preload_ids(ids_db, ["aaaaaaaa"], [])
    top_up_ids(ids_db, 2)
    cursor = ids_db.cursor()
    cursor.execute(
        """
        SELECT CanonicalId, CreatedAt FROM canonical_ids WHERE Status = 'free' ORDER BY CanonicalId ASC 
        """
    )
    rows = cursor.fetchall()
    assert len(rows) == 2
    # this is the preloaded id, and is alphabetically first, so should be the first row
    assert rows[0][0] == "aaaaaaaa"
    assert rows[1][1] is not None
    # the id added by the topup should have a newer createdAt timestamp than the preloaded id
    # given that the granularity of the timestamp is seconds, and the test runs pretty quickly,
    # it's quite likely that they end up with the same timestamp.
    # The point of this test is mainly to check that createdAt is being set at all
    assert rows[1][1] >= rows[0][1]
