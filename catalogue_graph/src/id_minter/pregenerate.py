"""
Id Pregeneration

The goal of id pregeneration is to ensure that a pool of "free" ids is always available
for the id minter to assign to new works and concepts.

This allows the minter to operate without having to worry about id clashes.
"""
from id_minter import identifiers
from typing import List


def top_up_ids(conn, desired_count: int) -> list[str]:
    """
    Generate new ids until there are at least `desired_count` free ids available for minting,
    or until we've tried twice.
    """
    # Try it twice in case of id clashes.
    # As the overall id space is very large, the likelihood of clashes should be very low,
    # So if there are still not enough free ids after two attempts,
    # it's likely that there is a deeper issue that needs to be investigated.
    # If the first attempt has no clashes, then the second is a NOOP
    return _top_up_ids(conn, desired_count) + _top_up_ids(conn, desired_count)


def _top_up_ids(conn, desired_count: int) -> list[str]:
    # Get the current count of free ids from the database.
    current_free_id_count = get_free_id_count(conn)

    # If there are already enough free ids, do nothing.
    if current_free_id_count >= desired_count:
        return []

    # Otherwise, generate new ids until we have enough.
    ids_to_generate = desired_count - current_free_id_count
    new_ids = list(identifiers.generate_ids(ids_to_generate))

    # Save the new ids to the database.
    save_new_ids(conn, new_ids)
    return new_ids


def get_free_id_count(conn) -> int:
    cursor = conn.cursor()
    cursor.execute(
        """
        SELECT COUNT(*) FROM canonical_ids WHERE Status = 'free'
        """
    )
    (count,) = cursor.fetchone()
    return count


def save_new_ids(conn, new_ids: List[str]):
    cursor = conn.cursor()
    cursor.executemany(
        """
        INSERT OR IGNORE INTO canonical_ids (CanonicalId, Status) VALUES (?, 'free')
        """,
        [(new_id,) for new_id in new_ids]
    )
    conn.commit()
