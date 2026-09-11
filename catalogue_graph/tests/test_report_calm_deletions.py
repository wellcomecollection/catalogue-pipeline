import csv
import random
from pathlib import Path
from typing import Any

import pytest

from scripts.report_calm_deletions import (
    BATCH_SIZE,
    CalmApiError,
    CalmSearcher,
    SuspectResultError,
    build_search_envelope,
    find_missing,
    parse_search_result,
    report_calm_deletions,
    run_control_probe,
    scan_live_record_ids,
    sweep,
    write_report,
)

SEARCH_RESPONSE = (
    '<?xml version="1.0" encoding="utf-8"?>'
    '<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">'
    "<soap:Body>"
    '<SearchResponse xmlns="http://ds.co.uk/cs/webservices/">'
    "<SearchResult>{hits}</SearchResult>"
    "</SearchResponse>"
    "</soap:Body></soap:Envelope>"
)


def present_only(present: set[str]) -> Any:
    """A count_present that reports exactly the ids in `present` as held."""

    def count_present(record_ids: list[str]) -> int:
        return len([r for r in record_ids if r in present])

    return count_present


def test_build_search_envelope_quotes_record_ids() -> None:
    """CalmQuery.RecordId quotes the value, and the Calm API needs it."""
    envelope = build_search_envelope(["abc", "def"])

    assert '(RecordId="abc")OR(RecordId="def")' in envelope
    assert "<dbname>Catalog</dbname>" in envelope


def test_build_search_envelope_escapes_xml() -> None:
    assert "&amp;" in build_search_envelope(["a&b"])


def test_parse_search_result_reads_the_hit_count() -> None:
    assert parse_search_result(SEARCH_RESPONSE.format(hits=7)) == 7


def test_parse_search_result_rejects_a_non_xml_body() -> None:
    with pytest.raises(CalmApiError, match="not XML"):
        parse_search_result("<html>401 Unauthorized</html>\x00")


def test_parse_search_result_rejects_a_response_with_no_result() -> None:
    with pytest.raises(CalmApiError, match="No SearchResult"):
        parse_search_result("<Envelope><Body/></Envelope>")


def test_find_missing_returns_nothing_when_everything_is_present() -> None:
    ids = [f"rec{n:03}" for n in range(64)]

    assert find_missing(ids, present_only(set(ids))) == set()


def test_find_missing_costs_one_search_when_nothing_is_missing() -> None:
    """The whole point of the group-testing algorithm: a clean batch is one call."""
    ids = [f"rec{n:03}" for n in range(512)]
    calls = []

    def counting(record_ids: list[str]) -> int:
        calls.append(len(record_ids))
        return len(record_ids)

    find_missing(ids, counting)

    assert calls == [512]


def test_find_missing_identifies_the_missing_ones() -> None:
    ids = [f"rec{n:03}" for n in range(64)]
    gone = {"rec005", "rec031", "rec062"}

    assert find_missing(ids, present_only(set(ids) - gone)) == gone


@pytest.mark.parametrize("gone_count", [1, 2, 7, 31, 63])
def test_find_missing_holds_for_a_range_of_deletion_counts(gone_count: int) -> None:
    ids = [f"rec{n:03}" for n in range(64)]
    gone = set(ids[:gone_count])

    assert find_missing(ids, present_only(set(ids) - gone)) == gone


def test_find_missing_returns_everything_when_nothing_is_present() -> None:
    """The failure signature the sweep guards against, at the algorithm level."""
    ids = [f"rec{n:03}" for n in range(64)]

    assert find_missing(ids, present_only(set())) == set(ids)


class FakeSearcher:
    def __init__(self, present: set[str]) -> None:
        self.present = present
        self.searches = 0

    def count_present(self, record_ids: list[str]) -> int:
        self.searches += 1
        return len([r for r in record_ids if r in self.present])


def test_run_control_probe_passes_when_calm_has_our_records() -> None:
    ids = [f"rec{n:03}" for n in range(50)]

    run_control_probe(FakeSearcher(set(ids)), ids, size=10)  # type: ignore[arg-type]


def test_run_control_probe_stops_a_run_against_an_unresponsive_calm() -> None:
    """If CALM answers but finds nothing, the sweep would report the whole
    store as deleted, so the run must not get that far."""
    ids = [f"rec{n:03}" for n in range(50)]

    with pytest.raises(SuspectResultError, match="Control probe"):
        run_control_probe(FakeSearcher(set()), ids, size=10)  # type: ignore[arg-type]


def test_run_control_probe_stops_on_a_partial_shortfall() -> None:
    ids = [f"rec{n:03}" for n in range(50)]
    searcher = FakeSearcher(set(ids[:25]))

    with pytest.raises(SuspectResultError, match="cannot be told apart"):
        run_control_probe(searcher, ids, size=50)  # type: ignore[arg-type]


def test_sweep_collects_the_missing_records() -> None:
    ids = [f"rec{n:04}" for n in range(1000)]
    gone = {"rec0003", "rec0700"}
    searcher = FakeSearcher(set(ids) - gone)

    assert sorted(sweep(searcher, ids)) == sorted(gone)  # type: ignore[arg-type]


def test_sweep_aborts_when_a_whole_batch_comes_back_missing() -> None:
    ids = [f"rec{n:04}" for n in range(BATCH_SIZE)]
    searcher = FakeSearcher(set())

    with pytest.raises(SuspectResultError, match="wholly missing"):
        sweep(searcher, ids)  # type: ignore[arg-type]


def test_write_report_lists_the_ids(tmp_path: Path) -> None:
    output_path = str(tmp_path / "deletions.csv")

    write_report(["rec001", "rec002"], output_path)

    with open(output_path) as handle:
        assert list(csv.reader(handle)) == [
            ["record_id"],
            ["rec001"],
            ["rec002"],
        ]
    assert list(tmp_path.iterdir()) == [Path(output_path)]


class FakeDynamoResource:
    def __init__(self, items: list[dict]) -> None:
        class Paginator:
            def paginate(
                self,
                TableName: str,  # noqa: N803
                Segment: int,  # noqa: N803
                TotalSegments: int,  # noqa: N803
                **kwargs: Any,
            ) -> list[dict]:
                return [{"Items": items if Segment == 0 else []}]

        client = type("Client", (), {"get_paginator": lambda self, name: Paginator()})()
        self.meta = type("Meta", (), {"client": client})()


def test_scan_live_record_ids_skips_records_already_marked_deleted() -> None:
    """The deletion checker diverts these before testing, so checking them
    would spend API calls to learn nothing."""
    resource = FakeDynamoResource(
        [
            {"id": "live001"},
            {"id": "gone001", "isDeleted": True},
            {"id": "live002", "isDeleted": False},
        ]
    )

    assert scan_live_record_ids(resource) == ["live001", "live002"]


def test_scan_live_record_ids_refuses_an_empty_store() -> None:
    with pytest.raises(SuspectResultError, match="No live records"):
        scan_live_record_ids(FakeDynamoResource([{"id": "x", "isDeleted": True}]))


@pytest.mark.parametrize("limit", [0, -1])
def test_report_calm_deletions_rejects_a_limit_below_one(
    tmp_path: Path, limit: int
) -> None:
    with pytest.raises(ValueError, match="must be at least 1"):
        report_calm_deletions(str(tmp_path / "out.csv"), limit=limit)


def test_calm_searcher_rejects_more_hits_than_it_asked_for() -> None:
    class Client:
        def post(self, url: str, **kwargs: Any) -> Any:
            return type(
                "Response",
                (),
                {
                    "status_code": 200,
                    "text": SEARCH_RESPONSE.format(hits=5),
                    "cookies": {},
                },
            )()

    searcher = CalmSearcher(Client())  # type: ignore[arg-type]

    with pytest.raises(CalmApiError, match="should be impossible"):
        searcher.count_present(["a", "b"])


@pytest.mark.parametrize("seed", range(40))
def test_find_missing_agrees_with_the_truth_for_random_shapes(seed: int) -> None:
    """The algorithm is a port of a paper's equations, so check the result
    against the set it was meant to find rather than against itself."""
    rng = random.Random(seed)
    size = rng.randint(1, 300)
    ids = [f"rec{n:04}" for n in range(size)]
    gone = set(rng.sample(ids, rng.randint(0, size)))

    assert find_missing(ids, present_only(set(ids) - gone)) == gone


@pytest.mark.parametrize("seed", range(20))
def test_find_missing_never_costs_more_than_testing_each_one(seed: int) -> None:
    """Group testing is only worth it if it beats asking about every record."""
    rng = random.Random(seed)
    size = rng.randint(8, 300)
    ids = [f"rec{n:04}" for n in range(size)]
    # Sparse deletions, which is the case the sweep actually meets.
    gone = set(rng.sample(ids, max(1, size // 50)))
    present = set(ids) - gone
    calls = 0

    def counting(record_ids: list[str]) -> int:
        nonlocal calls
        calls += 1
        return len([r for r in record_ids if r in present])

    assert find_missing(ids, counting) == gone
    assert calls < size
