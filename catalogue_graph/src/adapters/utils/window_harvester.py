from __future__ import annotations

from collections.abc import Sequence
from datetime import UTC, datetime
from typing import Protocol, TypedDict

import structlog
from oai_pmh_client.client import OAIClient
from oai_pmh_client.exceptions import NoRecordsMatchError
from oai_pmh_client.models import Record

from models.incremental_window import IncrementalWindow

from .window_generator import WindowGenerator
from .window_store import WindowStore
from .window_summary import (
    WindowSummary,
)

logger = structlog.get_logger(__name__)


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

    def __init__(
        self,
        store: WindowStore,
        window_generator: WindowGenerator,
        client: OAIClient,
        metadata_prefix: str | None = None,
        set_spec: str | None = None,
        *,
        record_callback: WindowCallback,
        default_tags: dict[str, str] | None = None,
    ) -> None:
        self.client = client
        self.store = store
        self.window_generator = window_generator
        self.metadata_prefix = metadata_prefix
        self.set_spec = set_spec
        self.window_minutes = window_generator.window_minutes
        self.record_callback = record_callback
        self.default_tags = dict(default_tags) if default_tags else None

    def harvest_range(
        self,
        *,
        time_range: IncrementalWindow,
        max_windows: int | None = None,
        reprocess_successful_windows: bool = False,
    ) -> list[WindowSummary]:
        candidates = self.window_generator.generate_windows(time_range)
        reused: list[WindowSummary] = []

        if reprocess_successful_windows:
            pending = list(candidates)
        else:
            # Check which candidate windows were already processed
            status_map = self.store.load_status_map(
                start_time=time_range.start_time_utc, end_time=time_range.end_time_utc
            )
            pending = []

            for candidate in candidates:
                existing = status_map.get(candidate.to_iso_string())
                if existing and existing.get("state") == "success":
                    reused.append(WindowSummary.model_validate(existing))
                else:
                    pending.append(candidate)

        if max_windows is not None:
            pending = pending[:max_windows]

        logger.info(
            "Harvesting windows",
            pending=len(pending),
            candidates=len(candidates),
            window_range=time_range.to_formatted_string(),
        )

        new_summaries = self.harvest_windows(pending)

        if reprocess_successful_windows:
            return new_summaries
        combined = reused + new_summaries
        combined.sort(key=lambda summary: summary.window_start)

        return combined

    def harvest_windows(
        self, windows: Sequence[IncrementalWindow]
    ) -> list[WindowSummary]:
        logger.info("Processing windows sequentially", window_count=len(windows))
        summaries = [self.process_window(window) for window in windows]
        summaries.sort(key=lambda summary: summary.window_start)
        return summaries

    # ------------------------------------------------------------------
    # Core processing
    # ------------------------------------------------------------------
    def process_window(self, window: IncrementalWindow) -> WindowSummary:
        start = window.start_time_utc
        end = window.end_time_utc
        key = window.to_iso_string()
        attempts = 1
        record_ids: list[str] = []
        last_error: str | None = None
        custom_tags: dict[str, str] = {}

        logger.info(
            "Processing window",
            window=window.to_formatted_string(),
        )
        try:
            records_in_window = self.client.list_records(
                metadata_prefix=self.metadata_prefix,
                from_date=start,
                until_date=end,
                set_spec=self.set_spec,
            )

            records_with_ids: list[tuple[str, Record]] = []
            for idx, record in enumerate(records_in_window):
                identifier = self._record_identifier(record, start, idx)
                records_with_ids.append((identifier, record))
                record_ids.append(identifier)

            logger.info(
                "Downloaded raw records from client", record_count=len(record_ids)
            )

            callback_result = self.record_callback(records_with_ids)
            custom_tags |= callback_result.get("tags") or {}

            state = "success"
        except NoRecordsMatchError:
            state = "success"
        except Exception as exc:  # pragma: no cover - generic safety net
            last_error = repr(exc)
            state = "failed"
            record_ids = []

        updated_at = datetime.now(UTC)
        summary = WindowSummary(
            window_start=start,
            window_end=end,
            state=state,
            attempts=attempts,
            record_ids=record_ids,
            last_error=last_error,
            updated_at=updated_at,
            tags=self._merge_tags(custom_tags),
        )
        self.store.upsert(summary)

        if state == "success":
            logger.info(
                "Successfully processed window",
                window_key=key,
                record_count=len(record_ids),
            )
        else:
            logger.warning(
                "Failed to process window",
                window_key=key,
                attempts=attempts,
                last_error=last_error,
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
        raise ValueError(
            "Cannot harvest record without header.identifier "
            f"(window_start={window_start.isoformat()}, idx={idx})"
        )

    def _merge_tags(self, custom_tags: dict[str, str] | None) -> dict[str, str] | None:
        return {**(self.default_tags or {}), **(custom_tags or {})} or None
