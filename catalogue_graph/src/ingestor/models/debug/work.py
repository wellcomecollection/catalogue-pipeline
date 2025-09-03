from datetime import datetime
from typing import Literal

from pydantic import BaseModel

from ingestor.models.shared.identifier import Identifiers, SourceIdentifier
from ingestor.models.shared.merge_candidate import MergeCandidate


class SourceWorkDebugInformation(BaseModel):
    id: str
    identifier: SourceIdentifier
    version: int
    modifiedTime: datetime


class WorkDebug(BaseModel):
    source: SourceWorkDebugInformation
    mergedTime: datetime
    indexedTime: datetime
    mergeCandidates: list[MergeCandidate]


class VisibleWorkDebug(WorkDebug):
    redirectSources: list[Identifiers]


class InvisibleReason(BaseModel):
    type: Literal[
        "CopyrightNotCleared",
        "SourceFieldMissing",
        "InvalidValueInSourceField",
        "UnlinkedHistoricalLibraryMiro",
        "UnableToTransform",
        "MetsWorksAreNotVisible",
    ]
    info: str | None = None
    message: str | None = None


class InvisibleWorkDebug(WorkDebug):
    invisibilityReasons: list[InvisibleReason]


class DeletedReason(BaseModel):
    type: Literal["DeletedFromSource", "SuppressedFromSource", "TeiDeletedInMerger"]
    info: str | None = None


class DeletedWorkDebug(WorkDebug):
    deletedReason: DeletedReason
