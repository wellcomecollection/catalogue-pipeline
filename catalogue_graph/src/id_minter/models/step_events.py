"""Step Function event models for the ID Minter.

- StepFunctionMintingRequest    → input event (wraps SourceQueryRequest + job_id)
- StepFunctionMintingResponse   → output event
- StepFunctionMintingFailure    → per-identifier failure detail
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

from models.events import IncrementalWindow
from models.source_document_selection import SourceDocumentSelection
from utils.types import NonEmptyString


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
    def document_selection(self) -> SourceDocumentSelection:
        return SourceDocumentSelection(ids=self.source_identifiers, window=self.window)


class StepFunctionMintingFailure(BaseModel):
    """Detail for a single source identifier that failed to mint."""

    source_identifier: str
    error: str


class StepFunctionMintingResponse(BaseModel):
    """Result of a minting run: successful source IDs and per-ID failures."""

    successes: list[str]
    failures: list[StepFunctionMintingFailure]
    job_id: str
