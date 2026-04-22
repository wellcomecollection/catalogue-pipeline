from __future__ import annotations

import itertools
import json
from collections.abc import Iterable, Sequence
from datetime import UTC, datetime
from typing import Protocol, TypedDict

import structlog
from oai_pmh_client.client import OAIClient
from oai_pmh_client.exceptions import NoRecordsMatchError
from oai_pmh_client.models import Record
from pydantic import BaseModel

from models.incremental_window import IncrementalWindow

from .window_generator import WindowGenerator
from .window_store import WindowStore
from .window_summary import (
    WindowSummary,
)

logger = structlog.get_logger(__name__)

BATCH_SIZE = 10_000


def get_record_identifier(record: Record) -> str:
    """Return the OAI-PMH identifier from a record's header."""
    return record.header.identifier


class WindowCallback(Protocol):
    def __call__(
        self,
        records: list[tuple[str, Record]],
    ) -> WindowCallbackResult: ...


class WindowCallbackResult(TypedDict, total=False):
    tags: dict[str, str] | None


class BatchProgress(BaseModel):
    """Mutable accumulator for batch processing state within a window."""

    window: IncrementalWindow
    record_ids: list[str] = []
    changeset_ids: list[str] = []
    changed_record_ids: list[str] = []
    non_batch_tags: dict[str, str] = {}
    batches_succeeded: int = 0
    batches_failed: int = 0
    last_error: str | None = None

    @property
    def final_state(self) -> str:
        if self.batches_failed == 0 and self.last_error is None:
            return "success"
        if self.batches_succeeded > 0:
            return "partial_success"
        return "failed"

    def get_summary(self, is_final: bool) -> WindowSummary:
        tags = {
            "changeset_ids": json.dumps(self.changeset_ids),
            "record_ids_changed": json.dumps(self.changed_record_ids),
            **self.non_batch_tags,
        }

        state = self.final_state if is_final else "partial_success"

        return WindowSummary(
            window_start=self.window.start_time_utc,
            window_end=self.window.end_time_utc,
            state=state,
            attempts=1,
            record_ids=self.record_ids,
            last_error=self.last_error,
            updated_at=datetime.now(UTC),
            tags=tags or None,
        )


class WindowHarvestManager:
    """Coordinates windowed harvesting and bookkeeping."""

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

    def _records_with_ids(
        self, batch: Iterable[Record], progress: BatchProgress
    ) -> Iterable[tuple[str, Record]]:
        for record in batch:
            try:
                identifier = get_record_identifier(record)
                yield identifier, record
            except AttributeError as exc:
                raise ValueError(
                    "Cannot harvest record without header.identifier "
                    f"(window={progress.window.to_iso_string()})"
                ) from exc

    # ------------------------------------------------------------------
    # Core processing
    # ------------------------------------------------------------------
    def process_window(self, window: IncrementalWindow) -> WindowSummary:
        logger.info(
            "Processing window",
            window=window.to_formatted_string(),
        )

        progress = BatchProgress(window=window)

        try:
            records_in_window = self.client.list_records(
                metadata_prefix=self.metadata_prefix,
                from_date=window.start_time_utc,
                until_date=window.end_time_utc,
                set_spec=self.set_spec,
            )
            for batch in itertools.batched(records_in_window, BATCH_SIZE):
                self._process_single_batch(list(batch), progress)
        except NoRecordsMatchError:
            pass  # No records — fall through to final state
        except Exception as exc:  # pragma: no cover - generic safety net
            progress.last_error = repr(exc)

        summary = progress.get_summary(is_final=True)
        self.store.upsert(summary)

        if summary.state == "success":
            logger.info(
                "Successfully processed window",
                window_key=window.to_iso_string(),
                record_count=len(progress.record_ids),
            )
        else:
            logger.warning(
                "Failed to process window",
                window_key=window.to_iso_string(),
                state=summary.state,
                last_error=progress.last_error,
            )
        return summary

    def _process_single_batch(
        self,
        batch: list[Record],
        progress: BatchProgress,
    ) -> None:
        logger.info(
            "Processing batch",
            batch_size=len(batch),
            total_records_so_far=len(progress.record_ids) + len(batch),
        )

        try:
            batch_with_ids = list(self._records_with_ids(batch, progress))
            callback_result = self.record_callback(batch_with_ids)
            batch_tags = dict(callback_result.get("tags") or {})

            if "changeset_id" in batch_tags:
                progress.changeset_ids.append(batch_tags.pop("changeset_id"))
            if "record_ids_changed" in batch_tags:
                changed_ids = json.loads(batch_tags.pop("record_ids_changed"))
                progress.changed_record_ids.extend(changed_ids)

            all_tags = self._merge_tags(batch_tags)
            progress.non_batch_tags.update(all_tags)
            progress.record_ids.extend([r[0] for r in batch_with_ids])
            progress.batches_succeeded += 1

            self.store.upsert(progress.get_summary(is_final=False))
        except Exception as exc:
            progress.last_error = repr(exc)
            progress.batches_failed += 1
            logger.warning(
                "Batch processing failed",
                batch_size=len(batch),
                error=repr(exc),
            )

    def _merge_tags(self, custom_tags: dict[str, str]) -> dict[str, str]:
        return {**(self.default_tags or {}), **custom_tags}
