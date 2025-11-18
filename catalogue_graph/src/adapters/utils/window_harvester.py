from __future__ import annotations

import logging
from collections import Counter
from collections.abc import Callable, Sequence
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import UTC, datetime, timedelta
from typing import Any, TypedDict

from oai_pmh_client.client import OAIClient
from oai_pmh_client.exceptions import NoRecordsMatchError
from oai_pmh_client.models import Record
from pydantic import BaseModel, Field

from .window_store import IcebergWindowStore, WindowStatusRecord

logger = logging.getLogger(__name__)

RecordStoreCallback = Callable[[str, Record, datetime, datetime, int], bool | None]


class WindowSummary(TypedDict):
    window_key: str
    window_start: datetime
    window_end: datetime
    state: str
    attempts: int
    record_ids: list[str]
    last_error: str | None
    updated_at: datetime
    tags: dict[str, str] | None


WindowStatusRow = WindowSummary


class CoverageGap(BaseModel):
    start: datetime
    end: datetime


class WindowFailure(BaseModel):
    window_key: str
    window_start: datetime
    window_end: datetime
    attempts: int
    last_error: str | None


class WindowCoverageReport(BaseModel):
    range_start: datetime
    range_end: datetime
    total_windows: int = Field(0, ge=0)
    state_counts: dict[str, int] = Field(default_factory=dict)
    coverage_hours: float = 0.0
    coverage_gaps: list[CoverageGap] = Field(default_factory=list)
    failures: list[WindowFailure] = Field(default_factory=list)


class WindowHarvestManager:
    """Coordinates windowed harvesting and bookkeeping."""

    DEFAULT_WINDOW_MINUTES = 15
    DEFAULT_MAX_PARALLEL_REQUESTS = 3

    def __init__(
        self,
        client: OAIClient,
        store: IcebergWindowStore,
        metadata_prefix: str,
        set_spec: str,
        *,
        window_minutes: int | None = None,
        max_parallel_requests: int | None = None,
        record_callback: RecordStoreCallback | None = None,
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

    # ------------------------------------------------------------------
    # Window generation & scheduling
    # ------------------------------------------------------------------
    def generate_windows(
        self, start_time: datetime, end_time: datetime
    ) -> list[tuple[datetime, datetime]]:
        start_time = self._ensure_utc(start_time)
        end_time = self._ensure_utc(end_time)
        if start_time >= end_time:
            raise ValueError("start_time must be earlier than end_time")
        delta = timedelta(minutes=self.window_minutes)
        windows: list[tuple[datetime, datetime]] = []
        cursor = start_time
        while cursor < end_time:
            win_end = min(cursor + delta, end_time)
            windows.append((cursor, win_end))
            cursor = win_end
        logger.info(
            "Generated %d windows covering %s -> %s (size=%d minutes)",
            len(windows),
            start_time.isoformat(),
            end_time.isoformat(),
            self.window_minutes,
        )
        return windows

    def harvest_recent(
        self,
        *,
        start_time: datetime,
        end_time: datetime,
        max_windows: int | None = None,
        record_callback: RecordStoreCallback | None = None,
        reprocess_successful_windows: bool = False,
    ) -> list[WindowSummary]:
        start_time = self._ensure_utc(start_time)
        end_time = self._ensure_utc(end_time)
        candidates = self.generate_windows(start_time=start_time, end_time=end_time)
        status_map = self.store.load_status_map()
        if reprocess_successful_windows:
            pending = list(candidates)
        else:
            pending = [
                window
                for window in candidates
                if status_map.get(self._window_key(*window), {}).get("state")
                != "success"
            ]
        if max_windows is not None:
            pending = pending[:max_windows]
        logger.info(
            "Harvesting %d of %d windows between %s and %s",
            len(pending),
            len(candidates),
            start_time.isoformat(),
            end_time.isoformat(),
        )
        return self.harvest_windows(pending, record_callback=record_callback)

    def harvest_windows(
        self,
        windows: Sequence[tuple[datetime, datetime]],
        *,
        record_callback: RecordStoreCallback | None = None,
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
                    self.process_window, start, end, record_callback=callback
                ): (start, end)
                for start, end in windows
            }
            for future in as_completed(future_map):
                summaries.append(future.result())
        summaries.sort(key=lambda summary: summary["window_start"])
        return summaries

    # ------------------------------------------------------------------
    # Core processing
    # ------------------------------------------------------------------
    def process_window(
        self,
        start: datetime,
        end: datetime,
        *,
        record_callback: RecordStoreCallback | None = None,
    ) -> WindowSummary:
        start = self._ensure_utc(start)
        end = self._ensure_utc(end)
        key = self._window_key(start, end)
        attempts = 1
        record_ids: list[str] = []
        last_error: str | None = None
        state = "failed"
        callback = record_callback or self.record_callback
        logger.info("Processing window %s -> %s", start.isoformat(), end.isoformat())
        try:
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
                    "A record_callback must be supplied to persist harvested records."
                )
            if callback:
                for idx, record in enumerate(records_in_window, 1):
                    identifier = self._record_identifier(record, start, idx)
                    self._invoke_callback(callback, identifier, record, start, end, idx)
                    record_ids.append(identifier)
            state = "success"
        except NoRecordsMatchError:
            state = "success"
            record_ids = []
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
        tags = dict(self.default_tags) if self.default_tags else None
        summary: WindowSummary = {
            "window_key": key,
            "window_start": start,
            "window_end": end,
            "state": state,
            "attempts": attempts,
            "record_ids": record_ids,
            "last_error": last_error,
            "updated_at": updated_at,
            "tags": tags,
        }
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
    # Coverage & failure reporting
    # ------------------------------------------------------------------
    def coverage_report(
        self,
        *,
        range_start: datetime | None = None,
        range_end: datetime | None = None,
    ) -> WindowCoverageReport:
        rows = self._rows_in_range(range_start, range_end)
        if not rows:
            now = datetime.now(UTC)
            return WindowCoverageReport(
                range_start=self._ensure_utc(range_start) if range_start else now,
                range_end=self._ensure_utc(range_end) if range_end else now,
                total_windows=0,
            )
        sorted_rows = sorted(
            rows, key=lambda row: self._ensure_utc(row["window_start"])
        )
        first_start = (
            self._ensure_utc(range_start)
            if range_start
            else self._ensure_utc(sorted_rows[0]["window_start"])
        )
        last_end = (
            self._ensure_utc(range_end)
            if range_end
            else self._ensure_utc(sorted_rows[-1]["window_end"])
        )
        state_counts = Counter(row["state"] for row in sorted_rows)
        coverage_hours = (
            sum(
                (
                    self._ensure_utc(row["window_end"])
                    - self._ensure_utc(row["window_start"])
                ).total_seconds()
                for row in sorted_rows
            )
            / 3600.0
        )
        coverage_gaps: list[CoverageGap] = []
        rolling_end = self._ensure_utc(sorted_rows[0]["window_end"])
        for row in sorted_rows[1:]:
            start = self._ensure_utc(row["window_start"])
            end = self._ensure_utc(row["window_end"])
            if start > rolling_end:
                coverage_gaps.append(CoverageGap(start=rolling_end, end=start))
                rolling_end = end
            else:
                rolling_end = max(rolling_end, end)
        failures = [
            WindowFailure(
                window_key=row["window_key"],
                window_start=self._ensure_utc(row["window_start"]),
                window_end=self._ensure_utc(row["window_end"]),
                attempts=row["attempts"],
                last_error=row.get("last_error"),
            )
            for row in sorted_rows
            if row["state"] != "success"
        ]
        return WindowCoverageReport(
            range_start=first_start,
            range_end=last_end,
            total_windows=len(sorted_rows),
            state_counts=dict(state_counts),
            coverage_hours=coverage_hours,
            coverage_gaps=coverage_gaps,
            failures=failures,
        )

    def failed_windows(
        self,
        *,
        range_start: datetime | None = None,
        range_end: datetime | None = None,
    ) -> list[WindowStatusRow]:
        rows = self.store.list_by_state("failed")
        if not rows:
            return []
        start_bound = self._ensure_utc(range_start) if range_start else None
        end_bound = self._ensure_utc(range_end) if range_end else None
        typed_rows: list[WindowStatusRow] = []
        for row in rows:
            typed_row = self._coerce_row(row)
            if self._within_range(
                typed_row["window_start"],
                typed_row["window_end"],
                start_bound,
                end_bound,
            ):
                typed_rows.append(typed_row)
        return typed_rows

    def retry_failed_windows(
        self,
        *,
        range_start: datetime | None = None,
        range_end: datetime | None = None,
        limit: int | None = None,
        record_callback: RecordStoreCallback | None = None,
    ) -> list[WindowSummary]:
        failed = sorted(
            self.failed_windows(range_start=range_start, range_end=range_end),
            key=lambda row: self._ensure_utc(row["window_start"]),
        )
        if limit is not None:
            failed = failed[:limit]
        windows = [
            (
                self._ensure_utc(row["window_start"]),
                self._ensure_utc(row["window_end"]),
            )
            for row in failed
        ]
        logger.info(
            "Retrying %d failed windows%s",
            len(windows),
            f" (limit={limit})" if limit is not None else "",
        )
        return self.harvest_windows(windows, record_callback=record_callback)

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------
    @staticmethod
    def _window_key(start: datetime, end: datetime) -> str:
        return f"{start.isoformat()}_{end.isoformat()}"

    @staticmethod
    def _ensure_utc(dt: datetime) -> datetime:
        if dt.tzinfo is None:
            return dt.replace(tzinfo=UTC)
        return dt.astimezone(UTC)

    def _record_identifier(
        self, record: Record, window_start: datetime, idx: int
    ) -> str:
        header = getattr(record, "header", None)
        if header is not None:
            identifier = getattr(header, "identifier", None)
            if isinstance(identifier, str):
                return identifier
        return f"no-header-{window_start.isoformat()}-{idx}"

    def _invoke_callback(
        self,
        callback: RecordStoreCallback,
        identifier: str,
        record: Record,
        window_start: datetime,
        window_end: datetime,
        index: int,
    ) -> None:
        result = callback(identifier, record, window_start, window_end, index)
        if result is False:
            raise RuntimeError("Record callback reported failure")

    def _rows_in_range(
        self,
        range_start: datetime | None,
        range_end: datetime | None,
    ) -> list[WindowStatusRow]:
        scan = self.store.table.scan()
        arrow_table = scan.to_arrow()
        if arrow_table is None or arrow_table.num_rows == 0:
            return []
        start_bound = self._ensure_utc(range_start) if range_start else None
        end_bound = self._ensure_utc(range_end) if range_end else None
        rows: list[WindowStatusRow] = []
        for raw_row in arrow_table.to_pylist():
            typed_row = self._coerce_row(raw_row)
            if self._within_range(
                typed_row["window_start"],
                typed_row["window_end"],
                start_bound,
                end_bound,
            ):
                rows.append(typed_row)
        return rows

    def _coerce_row(self, row: dict[str, Any]) -> WindowStatusRow:
        window_start_value = row["window_start"]
        window_end_value = row["window_end"]
        updated_at_value = row.get("updated_at")

        def _coerce_datetime(value: Any) -> datetime:
            if isinstance(value, datetime):
                return self._ensure_utc(value)
            if isinstance(value, str):
                return self._ensure_utc(datetime.fromisoformat(value))
            raise TypeError(f"Unsupported datetime value: {value!r}")

        window_start = _coerce_datetime(window_start_value)
        window_end = _coerce_datetime(window_end_value)
        updated_at = (
            _coerce_datetime(updated_at_value)
            if updated_at_value is not None
            else window_end
        )
        record_ids_raw = row.get("record_ids")
        if record_ids_raw is None:
            record_ids: list[str] = []
        elif isinstance(record_ids_raw, (list, tuple)):
            record_ids = [str(item) for item in record_ids_raw]
        else:
            record_ids = [str(record_ids_raw)]
        last_error_value = row.get("last_error")
        last_error = None if last_error_value is None else str(last_error_value)
        return {
            "window_key": str(row["window_key"]),
            "window_start": window_start,
            "window_end": window_end,
            "state": str(row["state"]),
            "attempts": int(row["attempts"]),
            "record_ids": record_ids,
            "last_error": last_error,
            "updated_at": updated_at,
        }

    @staticmethod
    def _within_range(
        window_start: datetime,
        window_end: datetime,
        range_start: datetime | None,
        range_end: datetime | None,
    ) -> bool:
        if range_start and window_end <= WindowHarvestManager._ensure_utc(range_start):
            return False
        return not (
            range_end and window_start >= WindowHarvestManager._ensure_utc(range_end)
        )
