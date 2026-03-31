"""Step Function event models for the ID Minter.

- SourceQueryRequest            → validated source-selection config
- StepFunctionMintingRequest    → input event (wraps SourceQueryRequest + job_id)
- StepFunctionMintingResponse   → output event
- StepFunctionMintingFailure    → per-identifier failure detail
"""

from __future__ import annotations

from typing import Literal

from datetime import datetime, timedelta

from pydantic import BaseModel, ConfigDict, model_validator
from pydantic.alias_generators import to_camel

DEFAULT_WINDOW_MINUTES = 15


class SourceQueryRequest(BaseModel):
    """Describes which documents to fetch from Elasticsearch.

    Three mutually exclusive modes:

    - **identifiers**: supply ``source_identifiers`` to fetch specific docs.
    - **window**: supply ``end_time`` (and optionally ``start_time``,
      which defaults to ``end_time − 15 min``) for a time-range query.
    - **match_all**: omit both to fetch everything.

    Supplying *both* ``source_identifiers`` and a timestamp is invalid.
    """

    source_identifiers: list[str] | None = None
    end_time: datetime | None = None
    start_time: datetime | None = None

    @model_validator(mode="after")
    def validate_mode(self) -> SourceQueryRequest:
        has_ids = self.source_identifiers is not None
        has_time = self.end_time is not None or self.start_time is not None

        if has_ids and has_time:
            raise ValueError("Cannot specify both source_identifiers and a time window")

        if has_ids and any(
            sid.strip() == ""
            for sid in self.source_identifiers  # type: ignore[union-attr]
        ):
            raise ValueError("source_identifiers cannot contain empty strings")

        if has_time:
            if self.end_time is None:
                raise ValueError(
                    "end_time is required when specifying a time window "
                    "(start_time alone is not sufficient)"
                )
            if self.start_time is None:
                self.start_time = self.end_time - timedelta(
                    minutes=DEFAULT_WINDOW_MINUTES
                )
            if self.start_time >= self.end_time:
                raise ValueError("start_time must be before end_time")

        return self

    @property
    def mode_label(self) -> Literal["identifiers", "window", "match_all"]:
        if self.source_identifiers is not None:
            return "identifiers"
        if self.end_time is not None:
            return "window"
        return "match_all"


class StepFunctionMintingRequest(BaseModel):
    """Input event for the ID Minter step function.

    Accepts a flat JSON payload (camelCase or snake_case) and delegates
    source-query validation to :class:`SourceQueryRequest`.
    """

    model_config = ConfigDict(
        alias_generator=to_camel,
        validate_by_name=True,
        validate_by_alias=True,
    )

    source_identifiers: list[str] | None = None
    end_time: datetime | None = None
    start_time: datetime | None = None
    job_id: str

    @model_validator(mode="after")
    def validate_request(self) -> StepFunctionMintingRequest:
        # Delegate source-query validation (mutual exclusivity, defaults)
        sqr = self.source_query
        # Sync back the possibly-computed start_time
        self.start_time = sqr.start_time

        if self.job_id.strip() == "":
            raise ValueError("job_id cannot be empty")
        return self

    @property
    def source_query(self) -> SourceQueryRequest:
        """The validated source query derived from this request's fields."""
        return SourceQueryRequest(
            source_identifiers=self.source_identifiers,
            end_time=self.end_time,
            start_time=self.start_time,
        )


class StepFunctionMintingFailure(BaseModel):
    """Detail for a single source identifier that failed to mint."""

    source_identifier: str
    error: str


class StepFunctionMintingResponse(BaseModel):
    """Result of a minting run: successful source IDs and per-ID failures."""

    successes: list[str]
    failures: list[StepFunctionMintingFailure]
    job_id: str
