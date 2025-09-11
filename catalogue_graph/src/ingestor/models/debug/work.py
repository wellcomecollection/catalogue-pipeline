from datetime import datetime

from ingestor.models.denormalised.work import (
    DeletedDenormalisedWork,
    DenormalisedWork,
    InvisibleDenormalisedWork,
)
from ingestor.models.shared.deleted_reason import DeletedReason
from ingestor.models.shared.identifier import Identifiers, SourceIdentifier
from ingestor.models.shared.invisible_reason import InvisibleReason
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
    
    @classmethod
    def from_denormalised_work(cls, work: DenormalisedWork):
        source = SourceWorkDebugInformation(
            id=work.state.canonical_id,
            identifier=work.state.source_identifier,
            version=work.version,
            modified_time=work.state.source_modified_time,
        )
    
        return cls(
            source=source,
            merged_time=work.state.merged_time,
            indexed_time=datetime.now(),
            merge_candidates=work.state.merge_candidates
        )
    

class VisibleWorkDebug(BaseWorkDebug):
    redirect_sources: list[Identifiers]


class InvisibleWorkDebug(BaseWorkDebug):
    invisibility_reasons: list[InvisibleReason]

    @classmethod
    def from_denormalised_work(cls, work: InvisibleDenormalisedWork):
        return InvisibleWorkDebug(
            **BaseWorkDebug.from_denormalised_work(work).model_dump(),
            invisibility_reasons=work.invisibility_reasons
        )


class RedirectedWorkDebug(BaseWorkDebug):
    pass


class DeletedWorkDebug(BaseWorkDebug):
    deleted_reason: DeletedReason

    @classmethod
    def from_denormalised_work(cls, work: DeletedDenormalisedWork):
        return DeletedWorkDebug(
            **BaseWorkDebug.from_denormalised_work(work).model_dump(),
            deleted_reason=work.deleted_reason
        )


WorkDebug = (
    VisibleWorkDebug | InvisibleWorkDebug | DeletedWorkDebug | RedirectedWorkDebug
)
