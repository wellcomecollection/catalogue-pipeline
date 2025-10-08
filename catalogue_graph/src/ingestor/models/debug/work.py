from datetime import datetime

from ingestor.models.denormalised.work import (
    DeletedDenormalisedWork,
    DenormalisedWork,
    InvisibleDenormalisedWork,
    RedirectedDenormalisedWork,
    VisibleDenormalisedWork,
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


class WorkDebug(ElasticsearchModel):
    source: SourceWorkDebugInformation
    merged_time: datetime
    indexed_time: datetime
    merge_candidates: list[MergeCandidate]

    @classmethod
    def from_denormalised_work(cls, work: DenormalisedWork) -> "WorkDebug":
        debug = WorkDebug(
            source=SourceWorkDebugInformation(
                id=work.state.canonical_id,
                identifier=work.state.source_identifier,
                version=work.version,
                modified_time=work.state.source_modified_time,
            ),
            merged_time=work.state.merged_time,
            indexed_time=datetime.now(),
            merge_candidates=work.state.merge_candidates,
        ).model_dump()

        if isinstance(work, VisibleDenormalisedWork):
            return VisibleWorkDebug(**debug, redirect_sources=work.redirect_sources)
        if isinstance(work, RedirectedDenormalisedWork):
            return RedirectedWorkDebug(**debug)
        if isinstance(work, DeletedDenormalisedWork):
            return DeletedWorkDebug(**debug, deleted_reason=work.deleted_reason)
        if isinstance(work, InvisibleDenormalisedWork):
            return InvisibleWorkDebug(
                **debug, invisibility_reasons=work.invisibility_reasons
            )

        raise TypeError(f"Unknown work type '{type(work)}' for work {work}")


class VisibleWorkDebug(WorkDebug):
    redirect_sources: list[Identifiers]


class InvisibleWorkDebug(WorkDebug):
    invisibility_reasons: list[InvisibleReason]


class RedirectedWorkDebug(WorkDebug):
    pass


class DeletedWorkDebug(WorkDebug):
    deleted_reason: DeletedReason
