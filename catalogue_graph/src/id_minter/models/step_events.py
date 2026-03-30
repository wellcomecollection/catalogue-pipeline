"""Step Function event models for the ID Minter.

- StepFunctionMintingRequest  → input event (time-window based)
- StepFunctionMintingResponse → output event
- StepFunctionMintingFailure  → per-identifier failure detail
"""

from __future__ import annotations

from datetime import datetime, timedelta

from pydantic import BaseModel, ConfigDict, model_validator
from pydantic.alias_generators import to_camel

DEFAULT_WINDOW_MINUTES = 15


class StepFunctionMintingRequest(BaseModel):
    """Input event for the ID Minter step function.

    Supports three modes:

    - **IDs mode**: supply ``source_identifiers`` for targeted minting.
    - **Window mode**: supply ``end_time`` (and optionally ``start_time``,
      which defaults to ``end_time - 15 min``) for scheduled runs.
    - **Full reprocess mode**: omit both to process the entire index.

    Supplying *both* ``source_identifiers`` and a timestamp is invalid.
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
        has_ids = self.source_identifiers is not None
        has_time = self.end_time is not None or self.start_time is not None

        if has_ids and has_time:
            raise ValueError("Cannot specify both source_identifiers and a time window")

        if has_ids and any(sid.strip() == "" for sid in self.source_identifiers):  # type: ignore[union-attr]
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

        if self.job_id.strip() == "":
            raise ValueError("job_id cannot be empty")
        return self


class StepFunctionMintingFailure(BaseModel):
    """Detail for a single source identifier that failed to mint."""

    source_identifier: str
    error: str


class StepFunctionMintingResponse(BaseModel):
    """Result of a minting run: successful source IDs and per-ID failures."""

    successes: list[str]
    failures: list[StepFunctionMintingFailure]
    job_id: str
