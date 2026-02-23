"""
Id Pregeneration

The goal of id pregeneration is to ensure that a pool of "free" ids is always available
for the id minter to assign to new works and concepts.

This allows the minter to operate without having to worry about id clashes.
"""

import logging
from collections.abc import Iterable
from typing import Protocol

from id_minter import identifiers

logger = logging.getLogger(__name__)


class ShortfallError(RuntimeError):
    pass


class DBCursor(Protocol):
    def execute(self, q: str) -> None: ...

    def fetchone(self) -> tuple[int]: ...

    def executemany(self, q: str, args: list[tuple]) -> None: ...


class DBConnection[T: DBCursor](Protocol):
    def cursor(self) -> T: ...

    def commit(self) -> None: ...


def top_up_ids(conn: DBConnection, desired_count: int) -> None:
    """
    Generate new ids until there are at least `desired_count` free ids available for minting,
    or until we've tried twice.
    """
    assert desired_count > 0, f"desired_count must be positive, got {desired_count}"

    first_shortfall = _get_id_shortfall(conn, desired_count)
    if not first_shortfall:
        return

    # Try it twice in case of id clashes.
    # As the overall id space is very large, the likelihood of clashes should be very low,
    # So if there are still not enough free ids after two attempts,
    # it's likely that there is a deeper issue that needs to be investigated.
    _add_new_ids(conn, first_shortfall)
    second_shortfall = _get_id_shortfall(conn, desired_count)
    if not second_shortfall:
        return

    logger.info(
        f"first attempt to top up ids resulted in a shortfall of {second_shortfall} ids, retrying"
    )
    _add_new_ids(conn, second_shortfall)
    final_shortfall = _get_id_shortfall(conn, desired_count)

    if final_shortfall:
        raise ShortfallError(
            f"After two attempts to top up ids, there are still only {desired_count - final_shortfall} free ids available, which is less than the desired {desired_count}."
        )


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
    logger.info(f"Generating {ids_to_generate} new ids")
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
