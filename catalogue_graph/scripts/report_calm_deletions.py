"""Report which CALM records our adapter store holds that CALM no longer has.

This reads. It writes nothing to DynamoDB, publishes nothing, and produces a
CSV for a human to act on.

The real `calm_deletion_checker` service does the same search and then marks
what it finds, which is why this exists separately. Its `test` is a search for
a batch of record ids and a count of hits, and it treats every id the search
does not return as deleted. If CALM answers but returns nothing, because the
database is detached or the credentials get scoped down during the
decommission, the service marks the whole batch deleted and publishes those
deletions to the production catalogue. Nothing in it guards against that, and
CALM has just been frozen ahead of being switched off, which is when it is
least predictable. So: look first, decide, then mark.

Safety, in the order it runs:

 1. A control probe searches for record ids we already hold and requires CALM
    to return all of them. If it cannot find records that certainly exist, the
    run stops and nothing is reported as deleted.
 2. Any batch that comes back wholly missing aborts the run. At batch sizes in
    the hundreds that is a far better match for an API fault than for reality.
 3. Records already marked deleted are skipped, matching the service, which
    diverts them before the check.

Usage:
    uv run python scripts/report_calm_deletions.py --output-path /tmp/calm-deletions.csv
    uv run python scripts/report_calm_deletions.py --output-path /tmp/x.csv --limit 5000
"""

from __future__ import annotations

import argparse
import csv
import math
import os
import random
import time
from collections.abc import Callable, Iterator
from typing import Any
from xml.etree import ElementTree

import boto3
import httpx
import structlog

from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)

CALM_API_URL = "https://archives.wellcome.org/CalmAPI/ContentService.asmx"
CALM_TABLE_NAME = "vhs-calm-adapter"
DB_NAME = "Catalog"

USERNAME_SECRET = "calm_adapter/calm_api/username"
PASSWORD_SECRET = "calm_adapter/calm_api/password"

SOAP_NS = "http://www.w3.org/2003/05/soap-envelope"
CALM_NS = "http://ds.co.uk/cs/webservices/"

BATCH_SIZE = 512
"""Matches the deletion checker's configured batch size, so the number of API
calls this makes is what the real run would make."""

SCAN_SEGMENTS = 16

CONTROL_PROBE_SIZE = 20
"""Records drawn from our own store and expected back from CALM before the
sweep starts."""

REQUEST_TIMEOUT_SECONDS = 120.0


class CalmApiError(Exception):
    pass


class SuspectResultError(Exception):
    """CALM answered in a way that looks like a fault rather than a deletion."""


def _escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def build_search_envelope(record_ids: list[str]) -> str:
    """A soap12 Search over a disjunction of RecordId leaves.

    RecordId values are quoted, which `CalmQuery.RecordId` does too, and the
    Calm API needs.
    """
    expr = "OR".join(f'(RecordId="{_escape(record_id)}")' for record_id in record_ids)
    return (
        '<?xml version="1.0" encoding="utf-8"?>'
        f'<soap12:Envelope xmlns:soap12="{SOAP_NS}">'
        "<soap12:Body>"
        f'<Search xmlns="{CALM_NS}">'
        f"<dbname>{DB_NAME}</dbname>"
        "<elementSet>DC</elementSet>"
        f"<expr>{expr}</expr>"
        "</Search>"
        "</soap12:Body>"
        "</soap12:Envelope>"
    )


def parse_search_result(body: str) -> int:
    """Pull the hit count out of a SearchResponse."""
    try:
        root = ElementTree.fromstring(body)
    except ElementTree.ParseError as error:
        raise CalmApiError(f"Response was not XML: {error}") from error

    result = root.find(f".//{{{CALM_NS}}}SearchResult")
    if result is None or result.text is None:
        raise CalmApiError(f"No SearchResult in response: {body[:400]}")

    try:
        return int(result.text)
    except ValueError as error:
        raise CalmApiError(f"SearchResult was not a number: {result.text!r}") from error


class CalmSearcher:
    """Counts how many of a set of record ids CALM still holds."""

    def __init__(self, client: httpx.Client, url: str = CALM_API_URL) -> None:
        self.client = client
        self.url = url
        self.searches = 0

    def _post(self, action: str, envelope: str) -> httpx.Response:
        response = self.client.post(
            self.url,
            content=envelope.encode("utf8"),
            headers={
                "Content-Type": "text/xml; charset=utf-8",
                "SOAPAction": f"{CALM_NS}{action}",
            },
        )
        if response.status_code != 200:
            raise CalmApiError(f"{action} returned HTTP {response.status_code}")
        return response

    def _abandon(self, cookies: Any) -> None:
        """Release the session the search opened, as the adapter's client does.

        Calm allows a limited number of concurrent sessions, and a sweep opens
        one per search, so leaving them to expire would exhaust them.
        """
        envelope = (
            '<?xml version="1.0" encoding="utf-8"?>'
            f'<soap12:Envelope xmlns:soap12="{SOAP_NS}">'
            f'<soap12:Body><Abandon xmlns="{CALM_NS}" /></soap12:Body>'
            "</soap12:Envelope>"
        )
        try:
            self.client.post(
                self.url,
                content=envelope.encode("utf8"),
                headers={
                    "Content-Type": "text/xml; charset=utf-8",
                    "SOAPAction": f"{CALM_NS}Abandon",
                },
                cookies=cookies,
            )
        except httpx.HTTPError as error:
            logger.warning("Could not abandon session", error=str(error))

    def count_present(self, record_ids: list[str]) -> int:
        """How many of these ids CALM returns. Never more than it was asked for."""
        response = self._post("Search", build_search_envelope(record_ids))
        self.searches += 1
        self._abandon(response.cookies)

        hits = parse_search_result(response.text)
        if hits > len(record_ids):
            raise CalmApiError(
                f"Search returned {hits} results for {len(record_ids)} ids, "
                "which should be impossible and means the query is not doing "
                "what we think"
            )
        return hits


def _l(n: int, d: int) -> int:
    return math.ceil(math.log2(n / d)) - 1


def _k(n: int, d: int) -> int:
    return int(math.ceil(n / 2 ** _l(n, d))) - d


def _m(n: int, d: int) -> int:
    """Size of the next test set. Equation (11) in arxiv.org/abs/1407.2283."""
    if d <= n / 2:
        return int(n - 2 ** _l(n, d) * (d + _k(n, d) - 1))
    return _m(n, n - d)


def find_missing(
    record_ids: list[str], count_present: Callable[[list[str]], int]
) -> set[str]:
    """Which of these ids CALM no longer holds.

    A port of `DefectiveChecker.defectiveRecords`, the group-testing algorithm
    from Wang et al (arxiv.org/abs/1407.2283) that the deletion checker uses.
    Keeping the same algorithm means this makes the same number of API calls
    the real run would, so a sweep here also measures what that would cost.
    """

    def nested(items: list[str], missing_count: int) -> set[str]:
        if missing_count == 0:
            return set()
        if missing_count == len(items):
            return set(items)

        test_set = items[: _m(len(items), missing_count)]
        found_in_test = len(test_set) - count_present(test_set)
        return nested(test_set, found_in_test) | nested(
            items[len(test_set) :], missing_count - found_in_test
        )

    total_missing = len(record_ids) - count_present(record_ids)
    return nested(record_ids, total_missing)


def scan_live_record_ids(dynamodb_resource: Any) -> list[str]:
    """Every record id in the CALM store not already marked deleted.

    The deletion checker diverts already-deleted payloads before testing them,
    so re-checking them would cost API calls and change nothing.
    """
    client = dynamodb_resource.meta.client
    paginator = client.get_paginator("scan")

    live: list[str] = []
    already_deleted = 0
    for segment in range(SCAN_SEGMENTS):
        pages = paginator.paginate(
            TableName=CALM_TABLE_NAME,
            Segment=segment,
            TotalSegments=SCAN_SEGMENTS,
            ProjectionExpression="id, isDeleted",
        )
        for page in pages:
            for item in page["Items"]:
                if item.get("isDeleted", False):
                    already_deleted += 1
                else:
                    live.append(item["id"])

    logger.info(
        "Store scanned",
        live=len(live),
        already_deleted=already_deleted,
        total=len(live) + already_deleted,
    )
    if not live:
        raise SuspectResultError(
            "No live records in the store. Refusing to go on, because a run "
            "with nothing to check cannot tell us anything."
        )
    return live


def run_control_probe(
    searcher: CalmSearcher, live_ids: list[str], size: int = CONTROL_PROBE_SIZE
) -> None:
    """Require CALM to return records we know it should have.

    This is the guard that matters. If CALM has been detached or our
    credentials have lost their scope, every search comes back empty, and
    without this the sweep would report the entire store as deleted.
    """
    sample = random.sample(live_ids, min(size, len(live_ids)))
    present = searcher.count_present(sample)

    if present != len(sample):
        raise SuspectResultError(
            f"Control probe: CALM returned {present} of {len(sample)} records "
            "drawn from our own store. Some may genuinely be deleted, but a "
            "shortfall here cannot be told apart from CALM failing to answer, "
            "so the sweep would not be trustworthy. Investigate before rerunning."
        )

    logger.info("Control probe passed", records=len(sample))


def _batches(items: list[str], size: int) -> Iterator[list[str]]:
    for start in range(0, len(items), size):
        yield items[start : start + size]


def sweep(searcher: CalmSearcher, live_ids: list[str]) -> list[str]:
    """Walk the store in batches, collecting the ids CALM no longer has."""
    missing: list[str] = []
    started_at = time.time()

    for number, batch in enumerate(_batches(live_ids, BATCH_SIZE), start=1):
        batch_missing = find_missing(batch, searcher.count_present)

        if len(batch_missing) == len(batch):
            raise SuspectResultError(
                f"Batch {number} came back wholly missing, all {len(batch)} of "
                "it. At this size that is a much better match for an API fault "
                f"than for reality. Stopping with {len(missing)} found so far."
            )

        missing.extend(sorted(batch_missing))
        elapsed = max(time.time() - started_at, 1e-6)
        logger.info(
            "Swept batch",
            batch=number,
            checked=min(number * BATCH_SIZE, len(live_ids)),
            total=len(live_ids),
            missing_so_far=len(missing),
            searches=searcher.searches,
            records_per_second=round(
                min(number * BATCH_SIZE, len(live_ids)) / elapsed, 1
            ),
        )

    return missing


def write_report(missing: list[str], output_path: str) -> None:
    partial_path = f"{output_path}.partial"
    with open(partial_path, "w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["record_id"])
        writer.writerows([record_id] for record_id in missing)
    os.replace(partial_path, output_path)
    logger.info("Report written", path=output_path, records=len(missing))


def get_calm_credentials(session: Any) -> tuple[str, str]:
    secrets = session.client("secretsmanager")
    username = secrets.get_secret_value(SecretId=USERNAME_SECRET)["SecretString"]
    password = secrets.get_secret_value(SecretId=PASSWORD_SECRET)["SecretString"]
    return username, password


def report_calm_deletions(
    output_path: str,
    *,
    limit: int | None = None,
    session: Any | None = None,
) -> list[str]:
    if limit is not None and limit < 1:
        raise ValueError(f"--limit must be at least 1, got {limit}")

    session = session or boto3.Session()

    live_ids = scan_live_record_ids(session.resource("dynamodb"))
    if limit is not None:
        logger.warning(
            "Checking part of the store only. The report this writes is not a "
            "complete account of what CALM has lost.",
            limit=limit,
            live=len(live_ids),
        )
        live_ids = live_ids[:limit]

    username, password = get_calm_credentials(session)

    with httpx.Client(
        auth=(username, password), timeout=REQUEST_TIMEOUT_SECONDS
    ) as client:
        searcher = CalmSearcher(client)
        run_control_probe(searcher, live_ids)
        missing = sweep(searcher, live_ids)

    write_report(missing, output_path)
    logger.info(
        "Done",
        checked=len(live_ids),
        missing=len(missing),
        searches=searcher.searches,
    )
    return missing


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Report CALM records our store holds that CALM no longer has"
    )
    parser.add_argument(
        "--output-path",
        required=True,
        metavar="PATH",
        help="Where to write the CSV of missing record ids",
    )
    parser.add_argument(
        "--limit",
        type=int,
        metavar="N",
        help="Check only the first N live records, to try the run out before sweeping all of them",
    )
    args = parser.parse_args()

    setup_logging(
        ExecutionContext(trace_id=get_trace_id(), pipeline_step="report_calm_deletions")
    )

    report_calm_deletions(args.output_path, limit=args.limit)


if __name__ == "__main__":
    main()
