"""
Id Pregeneration

The goal of id pregeneration is to ensure that a pool of "free" ids is always available
for the id minter to assign to new works and concepts.

This allows the minter to operate without having to worry about id clashes.
"""

from collections.abc import Iterable

import structlog

from id_minter import identifiers
from id_minter.database import DBConnection

logger = structlog.get_logger(__name__)


class ShortfallError(RuntimeError):
    pass


def top_up_ids(conn: DBConnection, desired_count: int) -> None:
    """
    Generate new ids until there are at least `desired_count` free ids available for minting,
    or until we've tried twice.
    """
    assert desired_count > 0, f"desired_count must be positive, got {desired_count}"

    first_shortfall = _get_id_shortfall(conn, desired_count)
    if not first_shortfall:
        return

    logger.info(
        "First attempt to top up ids resulted in a shortfall, retrying",
        first_shortfall=first_shortfall,
    )
    # Try it twice in case of id clashes.
    # As the overall id space is very large, the likelihood of clashes should be very low,
    # So if there are still not enough free ids after two attempts,
    # it's likely that there is a deeper issue that needs to be investigated.
    _add_new_ids(conn, first_shortfall)
    second_shortfall = _get_id_shortfall(conn, desired_count)
    if not second_shortfall:
        return

    _add_new_ids(conn, second_shortfall)
    final_shortfall = _get_id_shortfall(conn, desired_count)

    if final_shortfall:
        logger.error(
            "Failed to top up ids after two attempts.",
            free_ids=desired_count - final_shortfall,
            final_shortfall=final_shortfall,
            desired_count=desired_count,
        )
        raise ShortfallError("Failed to make up the ids shortfall.")


def _get_id_shortfall(conn: DBConnection, desired_count: int) -> int:
    # Get the current count of free ids from the database.
    current_free_id_count = get_free_id_count(conn)

    # If there are already enough free ids, do nothing.
    if current_free_id_count < desired_count:
        # Otherwise, generate new ids until we have enough.
        return desired_count - current_free_id_count
    # more than enough is enough, don't return negative numbers
    return 0


def _add_new_ids(conn: DBConnection, ids_to_generate: int) -> None:
    logger.info("Adding new ids", count=ids_to_generate)
    save_new_ids(conn, identifiers.generate_ids(ids_to_generate))


def get_free_id_count(conn: DBConnection) -> int:
    cursor = conn.cursor()
    cursor.execute(
        """
        SELECT COUNT(*) FROM canonical_ids WHERE Status = 'free'
        """
    )
    (count,) = cursor.fetchone()
    assert isinstance(count, int)
    return count


def save_new_ids(conn: DBConnection, new_ids: Iterable[str]) -> None:
    ids_list = [(new_id,) for new_id in new_ids]
    if ids_list:
        cursor = conn.cursor()
        cursor.executemany(
            """
            INSERT IGNORE INTO canonical_ids (CanonicalId, Status) VALUES (%s, 'free')
            """,
            ids_list,
        )
        conn.commit()
