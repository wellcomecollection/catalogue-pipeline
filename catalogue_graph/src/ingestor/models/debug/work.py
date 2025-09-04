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


class BaseWorkDebug(BaseModel):
    source: SourceWorkDebugInformation
    mergedTime: datetime
    indexedTime: datetime
    mergeCandidates: list[MergeCandidate]


class VisibleWorkDebug(BaseWorkDebug):
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


class InvisibleWorkDebug(BaseWorkDebug):
    invisibilityReasons: list[InvisibleReason]


class RedirectedWorkDebug(BaseWorkDebug):
    pass


class DeletedReason(BaseModel):
    type: Literal["DeletedFromSource", "SuppressedFromSource", "TeiDeletedInMerger"]
    info: str | None = None


class DeletedWorkDebug(BaseWorkDebug):
    deletedReason: DeletedReason


WorkDebug = (
    VisibleWorkDebug | InvisibleWorkDebug | DeletedWorkDebug | RedirectedWorkDebug
)
