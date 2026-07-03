"""Tests for the OAI-PMH mark-published step.

The step stamps the windows named by the loader response's covered_window_keys
with a published_at tag so the trigger can resume from the last published
window.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

from adapters.extractors.oai_pmh.models.step_events import OAIPMHLoaderResponse
from adapters.steps.oai_pmh.folio_enrich import EnrichmentResponse
from adapters.steps.oai_pmh.mark_published import (
    MarkPublishedEvent,
    MarkPublishedResponse,
    MarkPublishedRuntime,
    handler,
)
from adapters.utils.window_store import WindowStore
from adapters.utils.window_summary import WindowSummary, published_at_from_tags
from models.incremental_window import IncrementalWindow
from tests.adapters.extractors.oai_pmh.conftest import (
    create_window_row,
    populate_window_store,
)

RANGE_START = datetime(2026, 7, 3, 10, 0, tzinfo=UTC)
STAMP_TIME = datetime(2026, 7, 3, 12, 0, tzinfo=UTC)


def _window(index: int) -> tuple[datetime, datetime]:
    start = RANGE_START + timedelta(minutes=15 * index)
    return start, start + timedelta(minutes=15)


def _key(row: WindowSummary) -> str:
    return row.window_key


def _run(
    store: WindowStore,
    keys: list[str],
    now: datetime = STAMP_TIME,
) -> MarkPublishedResponse:
    runtime = MarkPublishedRuntime(store=store, adapter_name="folio")
    event = MarkPublishedEvent(
        job_id="20260703T1200",
        adapter_type="folio",
        covered_window_keys=keys,
    )
    return handler(event, runtime=runtime, now=now)


class TestMarkPublished:
    def test_stamps_only_covered_success_rows(self, window_store: WindowStore) -> None:
        s0, e0 = _window(0)
        s1, e1 = _window(1)
        s2, e2 = _window(2)
        s3, e3 = _window(3)
        rows = [
            create_window_row(s0, e0, state="success"),
            create_window_row(s1, e1, state="failed"),
            create_window_row(s2, e2, state="partial_success"),
            create_window_row(s3, e3, state="success"),
        ]
        populate_window_store(window_store.table, rows)

        response = _run(window_store, [_key(row) for row in rows])

        assert response.windows_stamped == 2
        assert response.windows_skipped == 0
        assert response.last_published_end == e3

        stored = window_store.load_status_map()
        for key, row in stored.items():
            expected = key in {_key(rows[0]), _key(rows[3])}
            stamped = published_at_from_tags(row.tags) is not None
            assert stamped == expected

    def test_uncovered_rows_untouched(self, window_store: WindowStore) -> None:
        """Rows not named by the response (e.g. written by a concurrent
        execution) are never stamped, even when they sit in the same time
        range."""
        s0, e0 = _window(0)
        s1, e1 = _window(1)
        covered = create_window_row(s0, e0, state="success")
        concurrent = create_window_row(s1, e1, state="success")
        populate_window_store(window_store.table, [covered, concurrent])

        response = _run(window_store, [_key(covered)])

        assert response.windows_stamped == 1
        stored = window_store.load_status_map()
        assert published_at_from_tags(stored[_key(concurrent)].tags) is None
        assert published_at_from_tags(stored[_key(covered)].tags) is not None

    def test_skips_already_stamped_and_preserves_timestamp(
        self, window_store: WindowStore
    ) -> None:
        s0, e0 = _window(0)
        original = "2026-07-03T09:00:00+00:00"
        row = create_window_row(
            s0, e0, state="success", tags={"published_at": original}
        )
        populate_window_store(window_store.table, [row])

        response = _run(window_store, [_key(row)])

        assert response.windows_stamped == 0
        assert response.windows_skipped == 1
        assert response.last_published_end is None
        stored = window_store.load_status_map()
        assert (stored[_key(row)].tags or {})["published_at"] == original

    def test_garbage_stamp_is_re_stamped(self, window_store: WindowStore) -> None:
        """A published_at value that is not a valid timestamp (e.g. a null map
        value coerced to the string 'None') is treated as unstamped and
        overwritten with a real one."""
        s0, e0 = _window(0)
        row = create_window_row(s0, e0, state="success", tags={"published_at": "None"})
        populate_window_store(window_store.table, [row])

        response = _run(window_store, [_key(row)])

        assert response.windows_stamped == 1
        stored = window_store.load_status_map()
        assert (stored[_key(row)].tags or {})["published_at"] == STAMP_TIME.isoformat()

    def test_second_invocation_is_a_no_op(self, window_store: WindowStore) -> None:
        s0, e0 = _window(0)
        row = create_window_row(s0, e0, state="success")
        populate_window_store(window_store.table, [row])

        first = _run(window_store, [_key(row)])
        second = _run(window_store, [_key(row)], now=STAMP_TIME + timedelta(minutes=15))

        assert first.windows_stamped == 1
        assert second.windows_stamped == 0
        assert second.windows_skipped == 1
        stored = window_store.load_status_map()
        assert (stored[_key(row)].tags or {})["published_at"] == STAMP_TIME.isoformat()

    def test_empty_and_unknown_keys_are_ok(self, window_store: WindowStore) -> None:
        response = _run(window_store, [])
        assert response.windows_stamped == 0
        assert response.last_published_end is None

        response = _run(window_store, ["2026-07-03T10:00:00+00:00/PT15M"])
        assert response.windows_stamped == 0

    def test_changeset_tags_survive_stamping(self, window_store: WindowStore) -> None:
        s0, e0 = _window(0)
        tags = {
            "changeset_ids": '["cs-1", "cs-2"]',
            "upserted_record_count": "7",
            "extra": "value",
        }
        row = create_window_row(s0, e0, state="success", tags=tags)
        populate_window_store(window_store.table, [row])

        _run(window_store, [_key(row)])

        stored = window_store.load_status_map()
        stored_tags = stored[_key(row)].tags or {}
        assert stored_tags["changeset_ids"] == '["cs-1", "cs-2"]'
        assert stored_tags["upserted_record_count"] == "7"
        assert stored_tags["extra"] == "value"
        assert stored_tags["published_at"] == STAMP_TIME.isoformat()


class TestCoveredWindowKeys:
    """from_summaries names exactly the success windows the response carries."""

    def test_covers_success_summaries_only(self) -> None:
        s0, e0 = _window(0)
        s1, e1 = _window(1)
        s2, e2 = _window(2)
        success_new = create_window_row(s0, e0, state="success")
        failed = create_window_row(s1, e1, state="failed")
        success_reused = create_window_row(s2, e2, state="success")

        response = OAIPMHLoaderResponse.from_summaries(
            [success_new, failed, success_reused], job_id="20260703T1200"
        )

        assert response.covered_window_keys == [
            success_new.window_key,
            success_reused.window_key,
        ]


class TestStateMachinePayloadContract:
    """Pin the shapes the state machine feeds into the mark-published input.

    The loader response carries covered_window_keys itself; the SM threads it
    past the enrichment state with a JSONata Output merge, then injects
    adapter_type. These tests validate the merged payloads parse, using the
    exact serialization the steps emit.
    """

    def test_loader_response_shape_validates(self) -> None:
        s0, e0 = _window(0)
        row = create_window_row(s0, e0, state="success")
        loader_response = OAIPMHLoaderResponse.from_summaries(
            [row], job_id="20260703T1200"
        )
        loader_response.summaries = []  # suppressed in the ECS path
        merged = {
            **loader_response.model_dump(mode="json"),
            "adapter_type": "axiell",
        }
        event = MarkPublishedEvent.model_validate(merged)
        assert event.covered_window_keys == [row.window_key]

    def test_enrichment_response_shape_validates(self) -> None:
        s0, e0 = _window(0)
        row = create_window_row(s0, e0, state="success")
        enrichment_response = EnrichmentResponse(
            job_id="20260703T1200",
            changeset_ids=["cs-1"],
            items_changeset_ids=["items-cs-1"],
        )
        merged = {
            **enrichment_response.model_dump(mode="json"),
            "covered_window_keys": [row.window_key],
            "adapter_type": "folio",
        }
        event = MarkPublishedEvent.model_validate(merged)
        assert event.job_id == "20260703T1200"

    def test_window_key_round_trips_through_json(self) -> None:
        """The keys the loader emits match the keys stored on the rows."""
        s0, e0 = _window(0)
        row = create_window_row(s0, e0, state="success")
        assert (
            row.window_key
            == IncrementalWindow(start_time=s0, end_time=e0).to_iso_string()
        )
