from __future__ import annotations

import itertools
import json
from collections.abc import Iterable, Sequence
from datetime import UTC, datetime
from typing import Protocol

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


def get_record_identifier(record: Record) -> str | None:
    header = getattr(record, "header", None)
    if header is not None:
        identifier = getattr(header, "identifier", None)
        if isinstance(identifier, str):
            return identifier

    return None


class WindowCallbackResult(BaseModel):
    changeset_id: str | None = None
    upserted_record_ids: list[str] = []
    tags: dict[str, str] = {}


class WindowCallback(Protocol):
    def __call__(
        self,
        records: list[tuple[str, Record]],
    ) -> WindowCallbackResult: ...


class BatchProgress(BaseModel):
    """Mutable accumulator for batch processing state within a window."""

    window: IncrementalWindow
    record_ids: list[str] = []
    changeset_ids: list[str] = []
    upserted_record_ids: list[str] = []
    tags: dict[str, str] = {}
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
        all_tags = {
            **self.tags,
            "changeset_ids": json.dumps(self.changeset_ids),
            "record_ids_changed": json.dumps(self.upserted_record_ids),
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
            tags=all_tags,
        )

    @classmethod
    def from_existing_status(cls, status: WindowSummary) -> BatchProgress:
        """
        Create a progress object from an existing partially failed run and pre-populate it
        with successfully processed record IDs and changeset IDs to "resume" processing.
        """
        tags = status.tags or {}

        changeset_ids, upserted_record_ids = [], []
        if "changeset_ids" in tags:
            changeset_ids = json.loads(tags.pop("changeset_ids"))
        if "record_ids_changed" in tags:
            upserted_record_ids = json.loads(tags.pop("record_ids_changed"))

        return BatchProgress(
            window=IncrementalWindow(
                start_time=status.window_start, end_time=status.window_end
            ),
            record_ids=status.record_ids,
            changeset_ids=changeset_ids,
            upserted_record_ids=upserted_record_ids,
            tags=tags,
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
        self.default_tags = dict(default_tags or {})

    def harvest_range(
        self,
        *,
        time_range: IncrementalWindow,
        max_windows: int | None = None,
        reprocess_successful_windows: bool = False,
    ) -> list[WindowSummary]:
        candidate_windows = self.window_generator.generate_windows(time_range)
        pending_windows = []

        reused_summaries: list[WindowSummary] = []
        status_map: dict[str, WindowSummary] = {}

        if reprocess_successful_windows:
            pending_windows = list(candidate_windows)
        else:
            # Check which candidate windows were already processed
            status_map = self.store.load_status_map(
                start_time=time_range.start_time_utc, end_time=time_range.end_time_utc
            )

            for window in candidate_windows:
                existing_summary = status_map.get(window.to_iso_string())
                if existing_summary and existing_summary.state == "success":
                    reused_summaries.append(existing_summary)
                else:
                    pending_windows.append(window)

        if max_windows is not None:
            pending_windows = pending_windows[:max_windows]

        logger.info(
            "Harvesting windows",
            candidate_window_count=len(candidate_windows),
            pending_window_count=len(pending_windows),
            window_range=time_range.to_formatted_string(),
        )

        new_summaries = self.harvest_windows(pending_windows, status_map=status_map)

        combined_summaries = reused_summaries + new_summaries
        combined_summaries.sort(key=lambda summary: summary.window_start)
        return combined_summaries

    def harvest_windows(
        self,
        windows: Sequence[IncrementalWindow],
        *,
        status_map: dict[str, WindowSummary] | None = None,
    ) -> list[WindowSummary]:
        logger.info("Processing windows sequentially", window_count=len(windows))
        summaries = [
            self.process_window(
                window,
                existing_status=(status_map or {}).get(window.to_iso_string()),
            )
            for window in windows
        ]
        summaries.sort(key=lambda summary: summary.window_start)
        return summaries

    def process_window(
        self,
        window: IncrementalWindow,
        *,
        existing_status: WindowSummary | None = None,
    ) -> WindowSummary:
        logger.info("Processing window", window=window.to_iso_string())

        if existing_status and existing_status.state == "partial_success":
            progress = BatchProgress.from_existing_status(existing_status)
            ids_to_skip = set(existing_status.record_ids)
        else:
            progress = BatchProgress(window=window, tags=self.default_tags)
            ids_to_skip = set()

        try:
            records_in_window = self.client.list_records(
                metadata_prefix=self.metadata_prefix,
                from_date=window.start_time_utc,
                until_date=window.end_time_utc,
                set_spec=self.set_spec,
            )
            records_in_window = (
                r
                for r in records_in_window
                if get_record_identifier(r) not in ids_to_skip
            )
            for batch in itertools.batched(records_in_window, BATCH_SIZE):
                self._process_single_batch(list(batch), progress)
        except NoRecordsMatchError:
            pass  # No records — fall through to final state
        except Exception as e:
            progress.last_error = repr(e)

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
        logger.info("Processing batch", batch_size=len(batch))

        try:
            batch_with_ids = list(self._records_with_ids(batch, progress))
            result = self.record_callback(batch_with_ids)

            if result.changeset_id:
                progress.changeset_ids.append(result.changeset_id)
            progress.upserted_record_ids += result.upserted_record_ids
            progress.tags.update(result.tags)
            progress.record_ids.extend([r[0] for r in batch_with_ids])
            progress.batches_succeeded += 1
        except Exception as e:
            progress.last_error = repr(e)
            progress.batches_failed += 1
            logger.warning(
                "Failed to process batch",
                batch_size=len(batch),
                error=repr(e),
            )

        # Failing to persist the window summary for a specific batch is not a critical error. The only window summary
        # which must be persisted (and whose failure to persist should cause the pipeline to fail) is the final one.
        try:
            self.store.upsert(progress.get_summary(is_final=False))
        except Exception as e:
            logger.warning("Failed to persist batch window summary", error=repr(e))

    def _records_with_ids(
        self, batch: Iterable[Record], progress: BatchProgress
    ) -> Iterable[tuple[str, Record]]:
        for record in batch:
            identifier = get_record_identifier(record)
            if not identifier:
                raise ValueError(
                    "Cannot harvest record without header.identifier "
                    f"(window={progress.window.to_iso_string()})"
                )

            yield identifier, record
