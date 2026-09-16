"""The version encodings these tests compare.

The shipped encoding is imported from `ingestor.models.indexable.version` rather than
restated, so the tests cannot drift from what the ingestor actually writes. The others
are here to show, against a real cluster, what each rejected option does.
"""

from collections.abc import Callable
from datetime import UTC, datetime

from ingestor.models.indexable.version import (
    MERGED_TIME_BASE_SEC,
    MERGED_TIME_SCALE,
    MIN_SOURCE_TIME_SEC,
    version_from_modified_time,
    version_from_source_and_merged_time,
)

LONG_MAX = 2**63 - 1

Encoding = Callable[[str, str], int]


def parse(timestamp: str) -> datetime:
    return datetime.fromisoformat(timestamp)


def epoch_sec(timestamp: str) -> int:
    return int(parse(timestamp).timestamp())


def epoch_ms(timestamp: str) -> int:
    return int(parse(timestamp).timestamp() * 1000)


def shipped(source: str, merged: str) -> int:
    """What the ingestor writes: (sourceModifiedTime, mergedTime) to whole seconds."""
    return version_from_source_and_merged_time(parse(source), parse(merged))


def merged_millis(source: str, merged: str) -> int:
    """PR #3649. Cannot tell a stale source apart from a re-merge."""
    return version_from_modified_time(parse(merged))


def source_millis(source: str, merged: str) -> int:
    """Before PR #3649. Cannot order two merges of the same source record."""
    return version_from_modified_time(parse(source))


def digit_concatenation(source: str, merged: str) -> int:
    """Both epochs written end to end. 23 digits, so it overflows the version field."""
    return int(f"{epoch_sec(source)}{epoch_ms(merged)}")


def decimal_string(source: str, merged: str) -> str:
    """Both epochs as one decimal number, e.g. 1619481599.1761915398269."""
    return f"{epoch_sec(source)}.{epoch_ms(merged)}"


# Considered and not shipped: the same ordering packed as bit fields rather than decimal
# digits, which buys headroom at the cost of a version you cannot read by eye.
BIT_MERGED_BITS = 31


def bit_packed(source: str, merged: str) -> int:
    source_seconds = max(MIN_SOURCE_TIME_SEC, epoch_sec(source))
    merged_seconds = min(
        max(0, epoch_sec(merged) - MERGED_TIME_BASE_SEC), 2**BIT_MERGED_BITS - 1
    )
    packed: int = (source_seconds << BIT_MERGED_BITS) | merged_seconds
    return packed


def headroom() -> list[tuple[str, str, str]]:
    """When each field overflows, as dates rather than magnitudes."""

    def utc(seconds: int) -> str:
        return datetime.fromtimestamp(seconds, UTC).strftime("%Y-%m-%d")

    return [
        (
            "shipped",
            "source seconds overflow Long.MAX",
            utc(LONG_MAX // MERGED_TIME_SCALE),
        ),
        (
            "shipped",
            "merge field saturates and stops breaking ties",
            utc(MERGED_TIME_BASE_SEC + MERGED_TIME_SCALE - 1),
        ),
        (
            "bit_packed",
            "source seconds overflow Long.MAX",
            utc(LONG_MAX >> BIT_MERGED_BITS),
        ),
        (
            "bit_packed",
            "merge field saturates and stops breaking ties",
            utc(MERGED_TIME_BASE_SEC + 2**BIT_MERGED_BITS - 1),
        ),
    ]
