"""Elasticsearch versioning for the works ingestor, against a real local cluster.

Ingestor runs overlap by design: a reindex runs alongside the incremental pipeline on its
15-minute schedule. The external version is what decides which of two concurrent writes
of the same work survives, so these tests pin down what Elasticsearch accepts as a
version and that the shipped encoding orders writes the way we need, whatever order they
arrive in.

These need Docker, so they are marked `elasticsearch` and deselected by default.

Usage:
    uv run pytest -m elasticsearch

See README.md in this directory for what each part establishes.
"""

import itertools

import pytest
from documents import build_work, stored_work, write_work
from elasticsearch import Elasticsearch, helpers
from version_encodings import (
    LONG_MAX,
    Encoding,
    bit_packed,
    decimal_string,
    digit_concatenation,
    epoch_sec,
    merged_millis,
    shipped,
    source_millis,
)

pytestmark = pytest.mark.elasticsearch

# Two source states and two merge times, picked so source order and merge order disagree.
S_OLD = "2021-04-26T23:59:59.999999Z"
S_OLD_SAME_SECOND = "2021-04-26T23:59:59.000001Z"
S_NEW = "2026-09-10T09:00:00.000000Z"
M_EARLY = "2026-09-15T10:00:00.123456Z"
M_EARLY_SAME_SECOND = "2026-09-15T10:00:00.999999Z"
M_LATE = "2026-09-15T10:30:00.654321Z"
EPOCH = "1970-01-01T00:00:00Z"

# name, first write, second write, marker that must survive
SCENARIOS = [
    ("same-source-later-merge-arrives-second", (S_OLD, M_EARLY), (S_OLD, M_LATE), 2),
    ("same-source-earlier-merge-arrives-second", (S_OLD, M_LATE), (S_OLD, M_EARLY), 1),
    ("newer-source-earlier-merge-arrives-second", (S_OLD, M_LATE), (S_NEW, M_EARLY), 2),
    ("older-source-later-merge-arrives-second", (S_NEW, M_EARLY), (S_OLD, M_LATE), 1),
    ("identical-source-and-merge-rewritten", (S_OLD, M_EARLY), (S_OLD, M_EARLY), 2),
    (
        "source-changes-within-one-second-later-merge-wins",
        (S_OLD_SAME_SECOND, M_EARLY),
        (S_OLD, M_LATE),
        2,
    ),
    (
        "source-changes-within-one-second-earlier-merge-loses",
        (S_OLD_SAME_SECOND, M_LATE),
        (S_OLD, M_EARLY),
        1,
    ),
]

# Four states whose lexicographic (source, merge) maximum is state 4.
ARRIVAL_STATES = [
    (1, S_OLD, M_EARLY),
    (2, S_OLD, M_LATE),
    (3, S_NEW, M_EARLY),
    (4, S_NEW, M_LATE),
]


def _write(
    es_client: Elasticsearch,
    index: str,
    template: dict,
    doc_id: str,
    version: object,
    marker: int = 1,
    source: str = S_OLD,
    merged: str = M_EARLY,
) -> tuple[bool, int, str]:
    return write_work(
        es_client,
        index,
        doc_id,
        version,
        build_work(template, marker, source, merged),
    )


# --- what Elasticsearch accepts as an external version --------------------------------


@pytest.mark.parametrize(
    "version",
    [decimal_string(S_OLD, M_EARLY), "100.9"],
    ids=["the-suggested-decimal", "a-decimal-that-would-truncate-cleanly"],
)
def test_a_decimal_version_is_rejected(
    es_client: Elasticsearch, works_index: str, work_document: dict, version: str
) -> None:
    """An external version is an integer, and there is no rounding to fall back on."""
    accepted, status, reason = _write(
        es_client, works_index, work_document, "decimal", version
    )

    assert not accepted
    assert status == 400
    assert "Failed to parse long parameter [version]" in reason


def test_writing_both_epochs_end_to_end_overflows_the_version_field(
    es_client: Elasticsearch, works_index: str, work_document: dict
) -> None:
    """Source seconds followed by merge millis is 23 digits. Long.MAX is 19."""
    version = digit_concatenation(S_OLD, M_EARLY)
    assert len(str(version)) > len(str(LONG_MAX))

    accepted, status, reason = _write(
        es_client, works_index, work_document, "concatenated", version
    )

    assert not accepted
    assert status == 400
    assert "Failed to parse long parameter [version]" in reason


def test_long_max_is_the_upper_bound(
    es_client: Elasticsearch, works_index: str, work_document: dict
) -> None:
    accepted, _, _ = _write(es_client, works_index, work_document, "long-max", LONG_MAX)
    assert accepted

    accepted, status, _ = _write(
        es_client, works_index, work_document, "over-long-max", LONG_MAX + 1
    )
    assert not accepted
    assert status == 400


def test_a_negative_version_is_rejected(
    es_client: Elasticsearch, works_index: str, work_document: dict
) -> None:
    """Which rules out any encoding that could go negative for a pre-1970 source time."""
    accepted, status, reason = _write(
        es_client, works_index, work_document, "negative", -1
    )

    assert not accepted
    assert status == 400
    assert "illegal version value [-1]" in reason


def test_a_shipped_version_is_stored_exactly(
    es_client: Elasticsearch, works_index: str, work_document: dict
) -> None:
    """19 digits is past the 2**53 a double holds, so this is worth pinning down."""
    version = shipped(S_OLD, M_EARLY)
    assert len(str(version)) == 19

    accepted, _, _ = _write(es_client, works_index, work_document, "shipped", version)

    assert accepted
    assert stored_work(es_client, works_index, "shipped")[1] == version


# --- ordering -------------------------------------------------------------------------


@pytest.mark.parametrize(
    ("first", "second", "expected"),
    [scenario[1:] for scenario in SCENARIOS],
    ids=[scenario[0] for scenario in SCENARIOS],
)
def test_ordering_scenario(
    es_client: Elasticsearch,
    works_index: str,
    work_document: dict,
    first: tuple[str, str],
    second: tuple[str, str],
    expected: int,
) -> None:
    doc_id = f"scenario-{expected}-{first}-{second}"
    _write(
        es_client,
        works_index,
        work_document,
        doc_id,
        shipped(*first),
        marker=1,
        source=first[0],
        merged=first[1],
    )
    _write(
        es_client,
        works_index,
        work_document,
        doc_id,
        shipped(*second),
        marker=2,
        source=second[0],
        merged=second[1],
    )

    assert stored_work(es_client, works_index, doc_id)[0] == expected


def _arrival_order_losers(
    es_client: Elasticsearch, works_index: str, template: dict, encode: Encoding
) -> list[tuple[str, int]]:
    """Which of the 24 arrival orders leave the wrong work in place."""
    expected = max(
        ARRIVAL_STATES, key=lambda state: (epoch_sec(state[1]), epoch_sec(state[2]))
    )[0]

    losers = []
    for order in itertools.permutations(ARRIVAL_STATES):
        arrival = "".join(str(state[0]) for state in order)
        doc_id = f"arrival-{encode.__name__}-{arrival}"
        for marker, source, merged in order:
            _write(
                es_client,
                works_index,
                template,
                doc_id,
                encode(source, merged),
                marker=marker,
                source=source,
                merged=merged,
            )
        stored = stored_work(es_client, works_index, doc_id)[0]
        if stored != expected:
            losers.append((arrival, stored))
    return losers


@pytest.mark.parametrize("encode", [shipped, bit_packed], ids=lambda e: e.__name__)
def test_arrival_order_never_decides_the_winner(
    es_client: Elasticsearch, works_index: str, work_document: dict, encode: Encoding
) -> None:
    """The property the whole scheme exists for. bit_packed is the alternative that was
    considered and not shipped: it gives the same order with more headroom."""
    assert _arrival_order_losers(es_client, works_index, work_document, encode) == []


@pytest.mark.parametrize(
    "encode", [merged_millis, source_millis], ids=lambda e: e.__name__
)
def test_ordering_by_one_timestamp_alone_depends_on_arrival_order(
    es_client: Elasticsearch, works_index: str, work_document: dict, encode: Encoding
) -> None:
    """Why the change was needed. Each of the two encodings we have shipped leaves the
    wrong work in place for half the arrival orders. See wellcomecollection/platform#6686."""
    assert (
        len(_arrival_order_losers(es_client, works_index, work_document, encode)) == 12
    )


# --- migrating the live index ---------------------------------------------------------


def test_a_shipped_version_beats_the_epoch_millis_version_in_place(
    es_client: Elasticsearch, works_index: str, work_document: dict
) -> None:
    """Which is what lets the live index take the new scheme without a reindex."""
    doc_id = "migration-in-place"
    _write(es_client, works_index, work_document, doc_id, merged_millis(S_OLD, M_EARLY))

    accepted, _, _ = _write(
        es_client, works_index, work_document, doc_id, shipped(S_OLD, M_EARLY), marker=2
    )

    assert accepted


def test_reverting_to_epoch_millis_versions_is_refused(
    es_client: Elasticsearch, works_index: str, work_document: dict
) -> None:
    """So a rollback means rebuilding the index, not just redeploying the ingestor."""
    doc_id = "migration-rollback"
    _write(es_client, works_index, work_document, doc_id, shipped(S_OLD, M_LATE))

    accepted, status, _ = _write(
        es_client,
        works_index,
        work_document,
        doc_id,
        merged_millis(S_OLD, M_LATE),
        marker=2,
    )

    assert not accepted
    assert status == 409


def test_an_epoch_zero_source_time_stays_writable(
    es_client: Elasticsearch, works_index: str, work_document: dict
) -> None:
    """The floored source field is what keeps these documents above the epoch-millis
    version they already hold. Unfloored they pack below it and freeze."""
    doc_id = "migration-epoch-zero"
    stored_already = merged_millis(EPOCH, M_EARLY)
    _write(es_client, works_index, work_document, doc_id, stored_already, source=EPOCH)

    accepted, _, _ = _write(
        es_client,
        works_index,
        work_document,
        doc_id,
        shipped(EPOCH, M_EARLY),
        marker=2,
        source=EPOCH,
    )

    assert accepted
    assert shipped(EPOCH, M_EARLY) > stored_already


def test_an_unfloored_epoch_zero_source_time_would_freeze_the_document(
    es_client: Elasticsearch, works_index: str, work_document: dict
) -> None:
    """The failure the floor exists to prevent, shown rather than asserted in the abstract."""
    doc_id = "migration-epoch-zero-unfloored"
    _write(
        es_client,
        works_index,
        work_document,
        doc_id,
        merged_millis(EPOCH, M_EARLY),
        source=EPOCH,
    )
    unfloored = epoch_sec(M_EARLY) - 1577836800

    accepted, status, _ = _write(
        es_client, works_index, work_document, doc_id, unfloored, marker=2, source=EPOCH
    )

    assert not accepted
    assert status == 409


# --- the write path the ingestor uses -------------------------------------------------


def test_the_bulk_helper_stores_a_nineteen_digit_version(
    es_client: Elasticsearch, works_index: str, work_document: dict
) -> None:
    doc_id = "bulk-path"
    version = shipped(S_NEW, M_LATE)

    success, errors = helpers.bulk(
        es_client,
        [
            {
                "_index": works_index,
                "_id": doc_id,
                "_source": build_work(work_document, 1, S_NEW, M_LATE),
                "_version": version,
                "_version_type": "external_gte",
            }
        ],
        raise_on_error=False,
        stats_only=False,
    )

    assert (success, errors) == (1, [])
    assert stored_work(es_client, works_index, doc_id)[1] == version


def test_a_stale_bulk_write_bounces_as_a_version_conflict(
    es_client: Elasticsearch, works_index: str, work_document: dict
) -> None:
    """Which is the error type the indexer already counts as benign."""
    doc_id = "bulk-stale"
    _write(
        es_client,
        works_index,
        work_document,
        doc_id,
        shipped(S_NEW, M_LATE),
        source=S_NEW,
        merged=M_LATE,
    )

    _, bulk_errors = helpers.bulk(
        es_client,
        [
            {
                "_index": works_index,
                "_id": doc_id,
                "_source": build_work(work_document, 2, S_OLD, M_EARLY),
                "_version": shipped(S_OLD, M_EARLY),
                "_version_type": "external_gte",
            }
        ],
        raise_on_error=False,
        stats_only=False,
    )

    assert isinstance(bulk_errors, list)
    assert [
        action["error"]["type"] for error in bulk_errors for action in error.values()
    ] == ["version_conflict_engine_exception"]
