from datetime import UTC, datetime

import pytest

from ingestor.models.indexable.version import (
    MERGED_TIME_BASE_SEC,
    MERGED_TIME_SCALE,
    MIN_SOURCE_TIME_SEC,
    MIN_VERSION,
    version_from_modified_time,
    version_from_source_and_merged_time,
)

LONG_MAX = 2**63 - 1

S_OLD = datetime.fromisoformat("2021-04-26T23:59:59.999999Z")
S_OLD_SAME_SECOND = datetime.fromisoformat("2021-04-26T23:59:59.000001Z")
S_NEW = datetime.fromisoformat("2026-09-10T09:00:00Z")
M_EARLY = datetime.fromisoformat("2026-09-15T10:00:00.123456Z")
M_EARLY_SAME_SECOND = datetime.fromisoformat("2026-09-15T10:00:00.999999Z")
M_LATE = datetime.fromisoformat("2026-09-15T10:30:00.654321Z")
EPOCH = datetime.fromisoformat("1970-01-01T00:00:00Z")


def version(source: datetime, merged: datetime) -> int:
    return version_from_source_and_merged_time(source, merged)


def test_a_later_source_modification_always_wins() -> None:
    """Even when the older source merged afterwards, which a stale in-flight message
    produces: old source data carrying a fresh merge time."""
    assert version(S_NEW, M_EARLY) > version(S_OLD, M_LATE)


def test_merge_time_breaks_ties_on_the_same_source() -> None:
    """A re-merge leaves the source record untouched, so the source time alone cannot
    order two merges of the same work. See wellcomecollection/platform#6686."""
    assert version(S_OLD, M_LATE) > version(S_OLD, M_EARLY)


def test_an_unchanged_document_re_ingests_at_the_same_version() -> None:
    """external_gte lets an equal version through, so re-running a reindex is not a
    stream of version conflicts."""
    assert version(S_OLD, M_EARLY) == version(S_OLD, M_EARLY)


def test_ordering_is_lexicographic_on_both_timestamps() -> None:
    states = [
        (source, merged)
        for source in (EPOCH, S_OLD, S_NEW)
        for merged in (M_EARLY, M_LATE)
    ]
    by_version = sorted(states, key=lambda state: version(*state))
    by_timestamps = sorted(
        states,
        key=lambda state: (
            max(MIN_SOURCE_TIME_SEC, int(state[0].timestamp())),
            int(state[1].timestamp()),
        ),
    )
    assert by_version == by_timestamps


def test_versions_fit_in_a_signed_64_bit_integer() -> None:
    """An external version is a non-negative int64, so the packed fields have to fit."""
    far_future = datetime(2100, 1, 1, tzinfo=UTC)
    assert 0 <= version(far_future, far_future) <= LONG_MAX


@pytest.mark.parametrize(
    "source", [EPOCH, datetime(1970, 1, 10, tzinfo=UTC), S_OLD, S_NEW]
)
def test_a_packed_version_beats_the_epoch_millis_version_it_replaces(
    source: datetime,
) -> None:
    """The live index holds epoch-millis versions. Any document whose new version came
    out lower would refuse every later write, so the switch has to raise all of them.

    Records with a sourceModifiedTime at the Unix epoch are the ones at risk: unfloored
    they pack below the millis versions already stored.
    """
    assert version(source, M_EARLY) > version_from_modified_time(M_EARLY)


def test_sub_second_precision_is_dropped_from_both_fields() -> None:
    """Whole seconds on the source field is what makes room for the merge field."""
    assert version(S_OLD, M_EARLY) == version(S_OLD_SAME_SECOND, M_EARLY_SAME_SECOND)


def test_a_merge_time_before_the_base_does_not_go_negative() -> None:
    assert version(S_OLD, datetime(2019, 1, 1, tzinfo=UTC)) == (
        int(S_OLD.timestamp()) * MERGED_TIME_SCALE
    )


def test_a_merge_time_past_the_base_window_ties_rather_than_inverting() -> None:
    """Beyond the window the merge field saturates, which loses the tie-break but keeps
    the source ordering intact. Carrying into the source field would invert it."""
    beyond = datetime.fromtimestamp(MERGED_TIME_BASE_SEC + MERGED_TIME_SCALE, UTC)
    assert version(S_OLD, beyond) < version(S_NEW, M_EARLY)
    assert version(S_OLD, beyond) == version(S_OLD, M_EARLY) + (
        MERGED_TIME_SCALE - 1 - (int(M_EARLY.timestamp()) - MERGED_TIME_BASE_SEC)
    )


def test_modified_time_versions_are_floored_for_internal_counter_compatibility() -> (
    None
):
    assert version_from_modified_time(EPOCH) == MIN_VERSION
    assert version_from_modified_time(M_EARLY) == int(M_EARLY.timestamp() * 1000)
