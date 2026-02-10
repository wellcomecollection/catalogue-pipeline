from unittest import mock
import pytest
from id_minter.pregenerate import top_up_ids, get_free_id_count
from id_minter import identifiers


def preload_ids(conn, free_ids, assigned_ids):
    """
    Set up the database with a given set of free and assigned ids.
    """
    cursor = conn.cursor()
    cursor.executemany(
        """
        INSERT INTO canonical_ids (CanonicalId, Status) VALUES (?, ?)
        """,
        [(id, "free") for id in free_ids] +
        [(id, "assigned") for id in assigned_ids]
    )
    conn.commit()


def assertTableLooksLike(conn, rows: list[tuple[str, str]]):
    cursor = conn.cursor()
    cursor.execute(
        """
        SELECT CanonicalId, Status FROM canonical_ids ORDER BY CanonicalId
        """
    )
    actual_rows = cursor.fetchall()
    # this doesn't have to be a separate assertion,
    # but it's nice to have a clear error message if the number of rows is wrong,
    # rather than just a mismatch in the contents of the rows.
    assert len(actual_rows) == len(rows)
    assert actual_rows == sorted(rows)


def test_adequate_free_ids_does_nothing(sqlite_canonical_ids_db):
    conn = sqlite_canonical_ids_db
    # GIVEN
    free_ids = [
        f"aaaaaaa{i}" for i in range(2, 7)
    ]
    assigned_ids = [
        f"bbbbbbb{i}" for i in range(2, 5)
    ]
    preload_ids(conn, free_ids, assigned_ids)
    # WHEN
    top_up_ids(conn, 5)
    # THEN
    assertTableLooksLike(
        conn,
        [(id, "free") for id in free_ids] +
        [(id, "assigned") for id in assigned_ids]
    )


def test_inadequate_free_ids_generates_more(sqlite_canonical_ids_db):
    conn = sqlite_canonical_ids_db
    # GIVEN there are only three free ids
    free_ids = [
        f"aaaaaaa{i}" for i in range(2, 5)
    ]
    assigned_ids = [
        f"bbbbbbb{i}" for i in range(2, 5)
    ]

    preload_ids(conn, free_ids, assigned_ids)
    assert get_free_id_count(conn) < 5
    # WHEN
    new_ids = top_up_ids(conn, 5)
    assert get_free_id_count(conn) == 5
    # THEN
    assertTableLooksLike(
        conn,
        [(id, "free") for id in free_ids + new_ids] +
        [(id, "assigned") for id in assigned_ids]
    )


def test_clashes(sqlite_canonical_ids_db):
    conn = sqlite_canonical_ids_db
    # GIVEN there are only three free ids
    free_ids = [
        f"aaaaaaa{i}" for i in range(2, 5)
    ]
    assigned_ids = [
        f"bbbbbbb{i}" for i in range(2, 5)
    ]

    preload_ids(conn, free_ids, assigned_ids)
    print(get_free_id_count(conn))
    assert get_free_id_count(conn) < 8

    def mock_generate_ids(count: int):
        # At first, there are three free ids, so topping up to 8 should generate 5 new ids.
        if count == 5:
            yield from [
                "aaaaaaa3",  # this id clashes with an existing free id
                "aaaaaaa4",  # this id clashes with an existing free id
                "bbbbbbb4",  # this id clashes with an existing assigned id
                "aaaaaaa6",  # this id is new and should be added to the database
                "aaaaaaa7",  # this id is new and should be added to the database
            ]
        # After the first attempt, where 3 of the 5 clashed,
        # there should now be 5 free ids (the original 3, plus the 2 new ones),
        # so the second attempt to top up to 8 should generate 3 new ids.
        elif count == 3:
            yield from ["njn9f485", "vssjb422", "yp8psp82"]
        else:
            raise ValueError(f"Unexpected count: {count}")

    with mock.patch("id_minter.identifiers.generate_ids", new=mock_generate_ids):
        top_up_ids(conn, 8)
    assert get_free_id_count(conn) == 8
    assertTableLooksLike(
        conn,
        [(id, "free") for id in free_ids + ["aaaaaaa6", "aaaaaaa7", "njn9f485", "vssjb422", "yp8psp82"]] +
        [(id, "assigned") for id in assigned_ids]
    )


def test_persistent_clashes(sqlite_canonical_ids_db):
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
    conn = sqlite_canonical_ids_db
    free_ids = [
        f"aaaaaaa{i}" for i in range(2, 5)
    ]
    assigned_ids = [
        f"bbbbbbb{i}" for i in range(2, 5)
    ]

    preload_ids(conn, free_ids, assigned_ids)
    print(get_free_id_count(conn))
    assert get_free_id_count(conn) < 8

    def mock_generate_ids(count: int):
        # At first, there are three free ids, so topping up to 8 should generate 5 new ids.
        if count == 5:
            yield from [
                "aaaaaaa3",  # this id clashes with an existing free id
                "aaaaaaa4",  # this id clashes with an existing free id
                "bbbbbbb4",  # this id clashes with an existing assigned id
                "aaaaaaa6",  # this id is new and should be added to the database
                "aaaaaaa7",  # this id is new and should be added to the database
            ]
        # After the first attempt, where 3 of the 5 clashed,
        # there should now be 5 free ids (the original 3, plus the 2 new ones),
        # so the second attempt to top up to 8 should generate 3 new ids.
        elif count == 3:
            yield from ["aaaaaaa3", "vssjb422", "yp8psp82"]
        else:
            raise ValueError(f"Unexpected count: {count}")

    with mock.patch("id_minter.identifiers.generate_ids", new=mock_generate_ids):
        top_up_ids(conn, 8)

    assert get_free_id_count(conn) == 7
    assertTableLooksLike(
        conn,
        [(id, "free") for id in free_ids + ["aaaaaaa6", "aaaaaaa7", "vssjb422", "yp8psp82"]] +
        [(id, "assigned") for id in assigned_ids]
    )
