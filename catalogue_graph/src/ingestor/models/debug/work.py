from datetime import datetime
from typing import Literal

from pydantic import BaseModel

from ingestor.models.shared.identifier import Identifiers, SourceIdentifier
from ingestor.models.shared.merge_candidate import MergeCandidate
from ingestor.models.shared.serialisable import ElasticsearchModel


class SourceWorkDebugInformation(ElasticsearchModel):
    id: str
    identifier: SourceIdentifier
    version: int
    modified_time: datetime


class BaseWorkDebug(ElasticsearchModel):
    source: SourceWorkDebugInformation
    merged_time: datetime
    indexed_time: datetime
    merge_candidates: list[MergeCandidate]


class VisibleWorkDebug(BaseWorkDebug):
    redirect_sources: list[Identifiers]


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
    invisibility_reasons: list[InvisibleReason]


class RedirectedWorkDebug(BaseWorkDebug):
    pass


class DeletedReason(BaseModel):
    type: Literal["DeletedFromSource", "SuppressedFromSource", "TeiDeletedInMerger"]
    info: str | None = None


class DeletedWorkDebug(BaseWorkDebug):
    deleted_reason: DeletedReason


WorkDebug = (
    VisibleWorkDebug | InvisibleWorkDebug | DeletedWorkDebug | RedirectedWorkDebug
)
