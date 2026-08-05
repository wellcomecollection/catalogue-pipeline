from __future__ import annotations

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

import config
from models.find_work import FindWorkEvent
from models.incremental_window import IncrementalWindow
from models.source_scope import SourceScope
from utils.types import NonEmptyString

# Sized so worst-case identifier density (~105 ids/work in dense archives)
# stays well inside the minting Lambda's 900s timeout.
DEFAULT_MINT_PARTITION_SIZE = 10_000


class StepFunctionMintingRequest(BaseModel):
    """Input event for the ID Minter step function."""

    model_config = ConfigDict(
        alias_generator=to_camel,
        validate_by_name=True,
        validate_by_alias=True,
    )

    source_identifiers: list[NonEmptyString] | None = None
    window: IncrementalWindow | None = None
    job_id: NonEmptyString

    @property
    def source_scope(self) -> SourceScope:
        return SourceScope(ids=self.source_identifiers, window=self.window)


class MintingFindWorkEvent(FindWorkEvent):
    """Input for the id-minter work-discovery step."""

    partition_size: int = DEFAULT_MINT_PARTITION_SIZE
    job_id: str | None = None

    @property
    def s3_service_prefix_parts(self) -> list[str]:
        return [config.ID_MINTER_S3_PREFIX, "find_work"]


class MintingFindWorkResult(BaseModel):
    partitions: list[StepFunctionMintingRequest]


class StepFunctionMintingFailure(BaseModel):
    """Detail for a single source identifier that failed to mint."""

    source_identifier: str
    error: str


class StepFunctionMintingResponse(BaseModel):
    """Result of a minting run: successful source IDs and per-ID failures."""

    successes: list[str]
    failures: list[StepFunctionMintingFailure]
    job_id: str
