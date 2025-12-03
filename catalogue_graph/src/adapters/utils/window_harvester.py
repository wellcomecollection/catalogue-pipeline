from __future__ import annotations

import logging
from collections.abc import Sequence
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import UTC, datetime
from typing import Protocol, TypedDict

from oai_pmh_client.client import OAIClient
from oai_pmh_client.exceptions import NoRecordsMatchError
from oai_pmh_client.models import Record

from utils.timezone import ensure_datetime_utc

from .window_generator import WindowGenerator
from .window_store import WindowStatusRecord, WindowStore
from .window_summary import (
    WindowKey,
    WindowSummary,
)

logger = logging.getLogger(__name__)


class WindowCallback(Protocol):
    def __call__(
        self,
        records: list[tuple[str, Record]],
    ) -> WindowCallbackResult: ...


class WindowCallbackResult(TypedDict, total=False):
    tags: dict[str, str] | None


class WindowHarvestManager:
    """Coordinates windowed harvesting and bookkeeping."""

    DEFAULT_WINDOW_MINUTES = 15
    DEFAULT_MAX_PARALLEL_REQUESTS = 3

    def __init__(
        self,
        store: WindowStore,
        client: OAIClient | None = None,
        metadata_prefix: str | None = None,
        set_spec: str | None = None,
        *,
        window_minutes: int | None = None,
        max_parallel_requests: int | None = None,
        record_callback: WindowCallback | None = None,
        default_tags: dict[str, str] | None = None,
    ) -> None:
        self.client = client
        self.store = store
        self.metadata_prefix = metadata_prefix
        self.set_spec = set_spec
        self.window_minutes = window_minutes or self.DEFAULT_WINDOW_MINUTES
        self.max_parallel_requests = (
            max_parallel_requests or self.DEFAULT_MAX_PARALLEL_REQUESTS
        )
        self.record_callback = record_callback
        self.default_tags = dict(default_tags) if default_tags else None
        self.window_generator = WindowGenerator(
            window_minutes=self.window_minutes,
            allow_partial_final_window=True,
        )

    # ------------------------------------------------------------------
    # Window generation & scheduling
    # ------------------------------------------------------------------
    def generate_windows(
        self, start_time: datetime, end_time: datetime
    ) -> list[tuple[datetime, datetime]]:
        """Generate aligned time windows between start_time and end_time.

        Delegates to the WindowGenerator instance.

        Args:
            start_time: Start of the time range (inclusive).
            end_time: End of the time range (exclusive).

        Returns:
            List of (window_start, window_end) tuples.

        Raises:
            ValueError: If start_time >= end_time.
        """
        return self.window_generator.generate_windows(start_time, end_time)

    def harvest_range(
        self,
        *,
        start_time: datetime,
        end_time: datetime,
        max_windows: int | None = None,
        record_callback: WindowCallback | None = None,
        reprocess_successful_windows: bool = False,
    ) -> list[WindowSummary]:
        start_time = ensure_datetime_utc(start_time)
        end_time = ensure_datetime_utc(end_time)
        candidates = self.generate_windows(start_time=start_time, end_time=end_time)
        callback = record_callback or self.record_callback
        reused: list[WindowSummary] = []

        if reprocess_successful_windows:
            pending = list(candidates)
        else:
            status_map = self.store.load_status_map()
            pending = []

            for window in candidates:
                start, end = window
                key = WindowKey.from_dates(start, end)
                existing = status_map.get(key)
                if existing and existing.get("state") == "success":
                    reused.append(WindowSummary.model_validate(existing))
                    continue
                pending.append(window)

        if max_windows is not None:
            pending = pending[:max_windows]

        logger.info(
            "Harvesting %d of %d windows between %s and %s",
            len(pending),
            len(candidates),
            start_time.isoformat(),
            end_time.isoformat(),
        )

        new_summaries = self.harvest_windows(
            pending,
            record_callback=callback,
        )

        if reprocess_successful_windows:
            return new_summaries
        combined = reused + new_summaries
        combined.sort(key=lambda summary: summary.window_start)

        return combined

    def harvest_windows(
        self,
        windows: Sequence[tuple[datetime, datetime]],
        *,
        record_callback: WindowCallback | None = None,
        max_parallel_requests: int | None = None,
    ) -> list[WindowSummary]:
        if not windows:
            return []
        summaries: list[WindowSummary] = []
        workers = (
            min(max_parallel_requests or self.max_parallel_requests, len(windows)) or 1
        )
        callback = record_callback or self.record_callback
        logger.info(
            "Dispatching %d windows with %d parallel workers", len(windows), workers
        )
        with ThreadPoolExecutor(max_workers=workers) as executor:
            future_map = {
                executor.submit(
                    self.process_window,
                    start,
                    end,
                    record_callback=callback,
                ): (start, end)
                for start, end in windows
            }
            for future in as_completed(future_map):
                summaries.append(WindowSummary.model_validate(future.result()))

        summaries.sort(key=lambda summary: summary.window_start)
        return summaries

    # ------------------------------------------------------------------
    # Core processing
    # ------------------------------------------------------------------
    def process_window(
        self,
        start: datetime,
        end: datetime,
        *,
        record_callback: WindowCallback | None = None,
    ) -> WindowSummary:
        start = ensure_datetime_utc(start)
        end = ensure_datetime_utc(end)
        key = WindowKey.from_dates(start, end)
        attempts = 1
        record_ids: list[str] = []
        last_error: str | None = None
        tags: dict[str, str] | None = (
            dict(self.default_tags) if self.default_tags else None
        )
        callback = record_callback or self.record_callback

        logger.info("Processing window %s -> %s", start.isoformat(), end.isoformat())
        try:
            if self.client is None:
                raise RuntimeError("Cannot process window without an OAIClient")

            records_in_window = list(
                self.client.list_records(
                    metadata_prefix=self.metadata_prefix,
                    from_date=start,
                    until_date=end,
                    set_spec=self.set_spec,
                )
            )
            if not callback and records_in_window:
                raise RuntimeError(
                    "A record callback must be supplied via record_callback to persist harvested records."
                )

            if callback:
                records_with_ids: list[tuple[str, Record]] = []
                for idx, record in enumerate(records_in_window, 1):
                    identifier = self._record_identifier(record, start, idx)
                    records_with_ids.append((identifier, record))
                    record_ids.append(identifier)

                callback_result = callback(records_with_ids)

                if callback_result and "tags" in callback_result:
                    tags = self._merge_tags(callback_result["tags"])

            state = "success"

        except NoRecordsMatchError:
            state = "success"
            record_ids = []
            tags = dict(self.default_tags) if self.default_tags else None

        except Exception as exc:  # pragma: no cover - generic safety net
            last_error = repr(exc)
            state = "failed"
            record_ids = []
            logger.warning(
                "Window %s failed after attempt %d: %s",
                key,
                attempts,
                last_error,
            )

        updated_at = datetime.now(UTC)
        summary = WindowSummary(
            window_start=start,
            window_end=end,
            state=state,
            attempts=attempts,
            record_ids=record_ids,
            last_error=last_error,
            updated_at=updated_at,
            tags=tags,
        )
        self.store.upsert(
            WindowStatusRecord(
                window_key=key,
                window_start=start,
                window_end=end,
                state=state,
                attempts=attempts,
                last_error=last_error,
                record_ids=tuple(record_ids),
                updated_at=updated_at,
                tags=tags,
            )
        )
        if state == "success":
            logger.info(
                "Window %s succeeded with %d record(s)",
                key,
                len(record_ids),
            )
        return summary

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------
    def _record_identifier(
        self, record: Record, window_start: datetime, idx: int
    ) -> str:
        header = getattr(record, "header", None)
        if header is not None:
            identifier = getattr(header, "identifier", None)
            if isinstance(identifier, str):
                return identifier
        return f"no-header-{window_start.isoformat()}-{idx}"

    def _merge_tags(self, custom_tags: dict[str, str] | None) -> dict[str, str] | None:
        base = dict(self.default_tags) if self.default_tags else {}
        if not custom_tags:
            return base or None
        merged = dict(base)
        merged.update(custom_tags)
        return merged
