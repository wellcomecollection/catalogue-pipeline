"""Step Function event models for the ID Minter.

Direct port of the Scala StepFunctionModels:
- StepFunctionMintingRequest  → input event
- StepFunctionMintingResponse → output event
- StepFunctionMintingFailure  → per-identifier failure detail
"""

from __future__ import annotations

from pydantic import BaseModel, model_validator


class StepFunctionMintingRequest(BaseModel):
    """A batch of source identifiers to mint canonical IDs for."""

    source_identifiers: list[str]
    job_id: str

    @model_validator(mode="after")
    def validate_request(self) -> StepFunctionMintingRequest:
        if any(sid.strip() == "" for sid in self.source_identifiers):
            raise ValueError("source_identifiers cannot contain empty strings")
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
