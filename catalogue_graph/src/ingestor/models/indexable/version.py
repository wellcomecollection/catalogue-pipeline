"""External Elasticsearch versions for indexed records.

Ingestor runs overlap by design: a reindex runs alongside the incremental pipeline on its
15-minute schedule. Both write the same documents, so writes go out with
`version_type=external_gte` and a version that has to order them. The stale write then
arrives with a lower version and bounces as a version conflict, which the indexer counts
as benign.
"""

from datetime import datetime

# Backwards compatibility with documents still versioned by Elasticsearch's internal
# counter, which starts at 1. Can go once every index has been rebuilt.
MIN_VERSION = 100

# A work's version packs two timestamps into one long, because an external version is a
# single non-negative int64: whole seconds of sourceModifiedTime above, whole seconds of
# mergedTime below. Two bare second epochs are 20 digits and do not fit in the 19 of
# Long.MAX, so mergedTime goes in as an offset from a base, which keeps it to 9 digits.
#
# The base cannot move later. Every version would drop, and every document would refuse
# writes until its index was rebuilt.
MERGED_TIME_BASE_SEC = 1577836800  # 2020-01-01T00:00:00Z
MERGED_TIME_SCALE = 10**9

# Some records carry a sourceModifiedTime at the Unix epoch. Packed unfloored they land
# around 2.1e8, below the epoch-millis versions those same documents already hold, and
# Elasticsearch would refuse every later write to them. No source record was modified in
# January 1970, so flooring the field costs nothing real.
MIN_SOURCE_TIME_SEC = 2_000_000  # 1970-01-24T00:00:00Z


def version_from_modified_time(modified_time: datetime) -> int:
    """Epoch millis, for records ordered by a single timestamp."""
    return max(MIN_VERSION, int(modified_time.timestamp() * 1000))


def version_from_source_and_merged_time(
    source_modified_time: datetime, merged_time: datetime
) -> int:
    """Order works by (sourceModifiedTime, mergedTime), both to whole seconds.

    A later source modification always wins. mergedTime only breaks ties between merges
    of the same source content, which is what a stale in-flight message produces: old
    source data carrying a fresh merge time. Ordering by either timestamp alone gets one
    of those two cases wrong. See wellcomecollection/platform#6686.

    Second precision on the source field is what makes room for the merge field, so two
    source modifications inside one second tie and the merge time decides between them.
    """
    source_seconds = max(MIN_SOURCE_TIME_SEC, int(source_modified_time.timestamp()))

    # Clamped so a merge time outside the base window degrades to a tie rather than
    # carrying into the source field and inverting the order outright.
    merged_seconds = int(merged_time.timestamp()) - MERGED_TIME_BASE_SEC
    merged_seconds = min(max(0, merged_seconds), MERGED_TIME_SCALE - 1)

    return source_seconds * MERGED_TIME_SCALE + merged_seconds
