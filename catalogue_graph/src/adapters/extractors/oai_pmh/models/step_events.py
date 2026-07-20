"""Generic step event models for OAI-PMH adapters.

These models define the data contracts between adapter steps (trigger, loader)
and can be used directly or extended by specific adapters.
"""

from __future__ import annotations

from datetime import datetime
from typing import Annotated, Any, Literal

from pydantic import Field, TypeAdapter, field_validator

from adapters.models.events import BaseAdapterEvent, BaseLoaderResponse
from adapters.utils.window_harvester import WindowSummaryTags
from adapters.utils.window_summary import WindowSummary
from models.incremental_window import IncrementalWindow

DEFAULT_ID_COMMIT_EVERY = 10_000
"""Records buffered before an id-mode commit.

Deliberately large. Every changeset id published to the transformer costs
roughly a full materialisation of the bib store, which is sorted by id and so
cannot prune a changeset read. Committing rarely keeps a big recovery down to a
handful of changesets rather than hundreds."""

MAX_IDS_PER_RUN = 50_000
"""Ceiling on ids in a single id-mode run.

Each id is a separate GetRecord with a politeness delay, so a mistaken paste
would otherwise launch a task running for days."""


class OAIPMHTriggerEvent(BaseAdapterEvent):
    """Event payload for the trigger step.

    The trigger step receives this event (typically from EventBridge scheduler)
    and computes the next harvesting window based on progress state.
    """

    now: datetime | None = None
    """Timestamp to use as 'now' for window calculations.
    If None, uses current time. Useful for testing and replay."""


class OAIPMHLoaderEvent(BaseAdapterEvent):
    """Event payload for the loader step in window mode.

    The loader step receives this event from the trigger and harvests
    records from the OAI-PMH endpoint within the specified window.
    """

    mode: Literal["window"] = "window"
    """Discriminator selecting the loader's window mode.

    Defaults to ``window`` so trigger payloads and stored events written before
    id mode existed stay valid."""

    window: IncrementalWindow
    """Time range to harvest records from."""

    metadata_prefix: str | None = None
    """OAI-PMH metadata prefix to request (e.g., 'oai_marcxml')."""

    set_spec: str | None = None
    """OAI-PMH set specification to filter records."""

    max_windows: int | None = None
    """Maximum number of sub-windows to process in this batch."""

    window_minutes: int | None = None
    """Duration of each sub-window in minutes."""

    allow_partial_final_window: bool | None = None
    """Whether to allow the final sub-window to be shorter than window_minutes."""


class OAIPMHIdLoaderEvent(BaseAdapterEvent):
    """Event payload for the loader step in id mode.

    Fetches an explicit list of record ids via OAI ``GetRecord`` instead of
    harvesting a time range. Used to repair records the source holds but the
    adapter store is missing, typically because they were written with
    datestamps inside windows that had already been harvested.

    ``job_id`` is required, as in window mode: the state machine mints one, and
    the published event needs it to correlate the downstream transformer run.
    """

    mode: Literal["ids"]
    """Discriminator selecting the loader's id mode."""

    ids: list[str]
    """Record ids to fetch, in ``<namespace>:<local-id>`` form."""

    commit_every: int = DEFAULT_ID_COMMIT_EVERY
    """Records buffered before committing a batch."""

    polite_delay_seconds: float = 0.3
    """Pause between GetRecord calls, to avoid hammering a flaky source."""

    metadata_prefix: str | None = None
    """OAI-PMH metadata prefix to request (e.g., 'oai_marcxml')."""

    @field_validator("ids")
    @classmethod
    def _check_run_size(cls, ids: list[str]) -> list[str]:
        # An empty list means the caller asked to recover nothing, which is
        # almost always an id query that came back empty. Fail rather than run a
        # no-op that reads like a success.
        if not ids:
            raise ValueError("No ids supplied; id mode needs at least one record id")
        if len(ids) > MAX_IDS_PER_RUN:
            raise ValueError(
                f"{len(ids)} ids exceeds the {MAX_IDS_PER_RUN} per-run ceiling; "
                f"split the work across several runs"
            )
        return ids


class AdapterRecoveryEvent(BaseAdapterEvent):
    """Shared base for the manually invoked recovery steps.

    These steps are run ad hoc against a chosen adapter rather than as a stage
    of a harvest, so unlike the trigger and loader they belong to no job and
    ``job_id`` is optional.
    """

    job_id: str | None = None  # type: ignore[assignment]


class OAIPMHLoaderResponse(BaseLoaderResponse):
    """Response from the loader step.

    Contains summaries of processed windows and identifiers for downstream steps.
    """

    summaries: list[WindowSummary] = Field(default_factory=list)
    """Status summaries for each processed sub-window."""

    changeset_ids: list[str] = Field(default_factory=list)
    """Identifiers for changesets created during this load."""

    covered_window_keys: list[str] = Field(default_factory=list)
    """Keys of the success windows whose changesets this response carries.

    The mark-published step stamps exactly these rows, so windows written by
    other runs (or rows the loader never re-emitted) are never marked
    published by this execution."""

    changed_record_count: int
    """Total number of records that changed in this batch."""

    job_id: str
    """Job identifier linking this response to the originating trigger."""

    @classmethod
    def from_summaries(
        cls,
        summaries: list[WindowSummary],
        job_id: str,
        extra_changeset_ids: list[str] | None = None,
        extra_upserted_record_count: int = 0,
    ) -> OAIPMHLoaderResponse:
        """Build a response from window summaries.

        Args:
            summaries: Window summaries whose tags carry per-window changeset
                ids and upsert counts.
            job_id: Job identifier linking the response to its trigger.
            extra_changeset_ids: Changeset ids created outside per-window tags
                (e.g. per-flush commits in the loader's buffered backfill mode).
            extra_upserted_record_count: Upserted records counted outside
                per-window tags (the per-flush counterpart of
                ``extra_changeset_ids``).
        """
        upserted_record_count = extra_upserted_record_count
        changeset_ids = list(extra_changeset_ids or [])

        for summary in summaries:
            if not summary.tags:
                continue

            tags = WindowSummaryTags.parse(summary.tags)
            upserted_record_count += tags.upserted_record_count
            changeset_ids += tags.changeset_ids

        return cls(
            summaries=summaries,
            job_id=job_id,
            changeset_ids=list(set(changeset_ids)),
            covered_window_keys=[
                summary.window_key
                for summary in summaries
                if summary.state == "success"
            ],
            changed_record_count=upserted_record_count,
        )


UNFETCHABLE_SAMPLE_SIZE = 100
"""Unfetchable ids carried inline on the response.

The publish state passes the whole response onward as its output, so an
unbounded list risks breaching the Step Functions payload limit after the store
writes have already succeeded. The full list goes to the report in S3."""


class OAIPMHIdLoaderResponse(BaseLoaderResponse):
    """Response from the loader step in id mode.

    Shares ``job_id``, ``changeset_ids`` and ``covered_window_keys`` with the
    window-mode response, so the state machine's publish and mark-published
    states consume either without a change.
    """

    changeset_ids: list[str] = Field(default_factory=list)
    """Identifiers for changesets created during this run."""

    covered_window_keys: list[str] = Field(default_factory=list)
    """Always empty. Id mode harvests no windows and writes no window-status
    rows, so there is nothing for the mark-published step to stamp. The field is
    present so that step accepts this response unchanged; stamping an empty list
    is a no-op."""

    changed_record_count: int
    """Total number of records that changed in this run."""

    job_id: str
    """Job identifier linking this response to its execution."""

    requested: int
    """Distinct ids the run was asked to fetch."""

    recovered: int
    """Ids fetched and written to the adapter store."""

    removed: int
    """Ids the source reports as no longer existing."""

    unfetchable_count: int
    """Ids neither returned nor reported gone, after the client's retries."""

    unfetchable_sample: list[str] = Field(default_factory=list)
    """First few unfetchable ids. The full list is in the report."""


LoaderEvent = Annotated[
    OAIPMHLoaderEvent | OAIPMHIdLoaderEvent, Field(discriminator="mode")
]
"""Either loader mode, selected by the ``mode`` discriminator."""

LoaderResponse = OAIPMHLoaderResponse | OAIPMHIdLoaderResponse

_LOADER_EVENT_ADAPTER: TypeAdapter[LoaderEvent] = TypeAdapter(LoaderEvent)


def validate_loader_event(payload: Any) -> LoaderEvent:
    """Validate a loader event payload into the model its mode selects.

    A tagged union requires the discriminator to be present, so a payload
    without ``mode`` would be rejected outright rather than falling back to the
    field default. The trigger step and every stored event predate id mode and
    carry no ``mode``, so treat its absence as window mode.
    """
    if isinstance(payload, dict) and "mode" not in payload:
        payload = {**payload, "mode": "window"}
    return _LOADER_EVENT_ADAPTER.validate_python(payload)
