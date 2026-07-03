"""Tests for the OAI-PMH mark-published step.

The step stamps success windows in the covered range with a published_at tag
so the trigger can resume from the last published window.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

from adapters.extractors.oai_pmh.models.step_events import (
    OAIPMHLoaderEvent,
    OAIPMHLoaderResponse,
)
from adapters.steps.oai_pmh.folio_enrich import EnrichmentResponse
from adapters.steps.oai_pmh.mark_published import (
    MarkPublishedEvent,
    MarkPublishedResponse,
    MarkPublishedRuntime,
    handler,
)
from adapters.utils.window_harvester import WindowSummaryTags
from adapters.utils.window_store import WindowStore
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


def _event(start: datetime, end: datetime) -> MarkPublishedEvent:
    return MarkPublishedEvent(
        job_id="20260703T1200",
        adapter_type="folio",
        window=IncrementalWindow(start_time=start, end_time=end),
    )


def _run(
    store: WindowStore,
    start: datetime,
    end: datetime,
    now: datetime = STAMP_TIME,
) -> MarkPublishedResponse:
    runtime = MarkPublishedRuntime(store=store, adapter_name="folio")
    return handler(_event(start, end), runtime=runtime, now=now)


class TestMarkPublished:
    def test_stamps_only_success_rows_in_range(self, window_store: WindowStore) -> None:
        s0, e0 = _window(0)
        s1, e1 = _window(1)
        s2, e2 = _window(2)
        s3, e3 = _window(3)
        populate_window_store(
            window_store.table,
            [
                create_window_row(s0, e0, state="success"),
                create_window_row(s1, e1, state="failed"),
                create_window_row(s2, e2, state="partial_success"),
                create_window_row(s3, e3, state="success"),
            ],
        )

        response = _run(window_store, s0, e3)

        assert response.windows_stamped == 2
        assert response.windows_skipped == 0
        assert response.last_published_end == e3

        stored = window_store.load_status_map()
        stamped = {
            key: WindowSummaryTags.parse(row.tags).published_at
            for key, row in stored.items()
        }
        success_keys = [
            create_window_row(s0, e0).window_key,
            create_window_row(s3, e3).window_key,
        ]
        for key, published_at in stamped.items():
            if key in success_keys:
                assert published_at == STAMP_TIME.isoformat()
            else:
                assert published_at is None

    def test_out_of_range_rows_untouched(self, window_store: WindowStore) -> None:
        s0, e0 = _window(0)
        s1, e1 = _window(1)
        populate_window_store(
            window_store.table,
            [
                create_window_row(s0, e0, state="success"),
                create_window_row(s1, e1, state="success"),
            ],
        )

        response = _run(window_store, s1, e1)

        assert response.windows_stamped == 1
        stored = window_store.load_status_map()
        row0 = stored[create_window_row(s0, e0).window_key]
        assert WindowSummaryTags.parse(row0.tags).published_at is None

    def test_skips_already_stamped_and_preserves_timestamp(
        self, window_store: WindowStore
    ) -> None:
        s0, e0 = _window(0)
        original = "2026-07-03T09:00:00+00:00"
        populate_window_store(
            window_store.table,
            [
                create_window_row(
                    s0, e0, state="success", tags={"published_at": original}
                )
            ],
        )

        response = _run(window_store, s0, e0)

        assert response.windows_stamped == 0
        assert response.windows_skipped == 1
        assert response.last_published_end is None
        stored = window_store.load_status_map()
        row = stored[create_window_row(s0, e0).window_key]
        assert WindowSummaryTags.parse(row.tags).published_at == original

    def test_second_invocation_is_a_no_op(self, window_store: WindowStore) -> None:
        s0, e0 = _window(0)
        populate_window_store(
            window_store.table, [create_window_row(s0, e0, state="success")]
        )

        first = _run(window_store, s0, e0)
        later = STAMP_TIME + timedelta(minutes=15)
        second = _run(window_store, s0, e0, now=later)

        assert first.windows_stamped == 1
        assert second.windows_stamped == 0
        assert second.windows_skipped == 1
        stored = window_store.load_status_map()
        row = stored[create_window_row(s0, e0).window_key]
        assert WindowSummaryTags.parse(row.tags).published_at == STAMP_TIME.isoformat()

    def test_empty_range_is_ok(self, window_store: WindowStore) -> None:
        s0, e0 = _window(0)
        response = _run(window_store, s0, e0)
        assert response.windows_stamped == 0
        assert response.windows_skipped == 0
        assert response.last_published_end is None

    def test_changeset_tags_survive_stamping(self, window_store: WindowStore) -> None:
        s0, e0 = _window(0)
        tags = WindowSummaryTags(
            changeset_ids=["cs-1", "cs-2"],
            upserted_record_count=7,
            other_tags={"extra": "value"},
        ).dump()
        populate_window_store(
            window_store.table,
            [create_window_row(s0, e0, state="success", tags=tags)],
        )

        _run(window_store, s0, e0)

        stored = window_store.load_status_map()
        parsed = WindowSummaryTags.parse(
            stored[create_window_row(s0, e0).window_key].tags
        )
        assert parsed.changeset_ids == ["cs-1", "cs-2"]
        assert parsed.upserted_record_count == 7
        assert parsed.other_tags == {"extra": "value"}
        assert parsed.published_at == STAMP_TIME.isoformat()


class TestStateMachinePayloadContract:
    """Pin the shapes the state machine merges into the mark-published input.

    The SM threads the trigger's window past the loader (and enrichment) with
    a JSONata Output merge, then injects adapter_type. These tests validate
    that the merged payloads parse, using the exact window serialization the
    trigger lambda emits.
    """

    def _window_payload(self) -> dict[str, str]:
        loader_event = OAIPMHLoaderEvent(
            job_id="20260703T1200",
            adapter_type="folio",
            window=IncrementalWindow(start_time=_window(0)[0], end_time=_window(0)[1]),
            metadata_prefix="marc21",
        )
        payload: dict[str, str] = loader_event.model_dump(mode="json")["window"]
        return payload

    def test_loader_response_shape_validates(self) -> None:
        loader_response = OAIPMHLoaderResponse(
            job_id="20260703T1200",
            changeset_ids=["cs-1"],
            changed_record_count=1,
            summaries=[],
        )
        merged = {
            **loader_response.model_dump(mode="json"),
            "window": self._window_payload(),
            "adapter_type": "folio",
        }
        event = MarkPublishedEvent.model_validate(merged)
        assert event.window.start_time_utc == _window(0)[0]

    def test_enrichment_response_shape_validates(self) -> None:
        enrichment_response = EnrichmentResponse(
            job_id="20260703T1200",
            changeset_ids=["cs-1"],
            items_changeset_ids=["items-cs-1"],
        )
        merged = {
            **enrichment_response.model_dump(mode="json"),
            "window": self._window_payload(),
            "adapter_type": "folio",
        }
        event = MarkPublishedEvent.model_validate(merged)
        assert event.job_id == "20260703T1200"
