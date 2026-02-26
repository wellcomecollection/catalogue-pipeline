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

# These will be removed once the migration is complete and the step is no longer needed. 

class MigrationRequest(BaseModel):
    """Request to migrate identifiers from a parquet S3 export into the new schema."""

    s3_bucket: str = "wellcomecollection-platform-id-minter"
    cluster_name: str = "identifiers-serverless"
    export_date: str
    truncate: bool = True
    batch_size: int = 1_000_000


class MigrationResponse(BaseModel):
    """Result of a migration run."""

    total_source_rows: int
    canonical_ids_inserted: int
    identifiers_inserted: int
    canonical_ids_verified: int
    identifiers_verified: int
    orphaned_identifiers: int
