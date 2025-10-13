from datetime import datetime

from ingestor.models.merged.work import (
    DeletedMergedWork,
    InvisibleMergedWork,
    MergedWork,
    RedirectedMergedWork,
    VisibleMergedWork,
)
from ingestor.models.shared.deleted_reason import DeletedReason
from ingestor.models.shared.invisible_reason import InvisibleReason
from ingestor.models.shared.merge_candidate import MergeCandidate
from models.pipeline.identifier import Identifiers, SourceIdentifier
from models.pipeline.serialisable import ElasticsearchModel


class WorkDebugSource(ElasticsearchModel):
    id: str
    identifier: SourceIdentifier
    version: int
    modified_time: datetime


class WorkDebug(ElasticsearchModel):
    source: WorkDebugSource
    merged_time: datetime
    indexed_time: datetime
    merge_candidates: list[MergeCandidate]

    @classmethod
    def _from_merged_work(cls, work: MergedWork) -> "WorkDebug":
        return WorkDebug(
            source=WorkDebugSource(
                id=work.state.canonical_id,
                identifier=work.state.source_identifier,
                version=work.version,
                modified_time=work.state.source_modified_time,
            ),
            merged_time=work.state.merged_time,
            indexed_time=datetime.now(),
            merge_candidates=work.state.merge_candidates,
        )


class VisibleWorkDebug(WorkDebug):
    redirect_sources: list[Identifiers]

    @classmethod
    def from_merged_work(cls, work: VisibleMergedWork) -> "VisibleWorkDebug":
        debug = WorkDebug._from_merged_work(work).model_dump()
        return VisibleWorkDebug(**debug, redirect_sources=work.redirect_sources)


class InvisibleWorkDebug(WorkDebug):
    invisibility_reasons: list[InvisibleReason]

    @classmethod
    def from_merged_work(cls, work: InvisibleMergedWork) -> "InvisibleWorkDebug":
        debug = WorkDebug._from_merged_work(work).model_dump()
        return InvisibleWorkDebug(
            **debug, invisibility_reasons=work.invisibility_reasons
        )


class RedirectedWorkDebug(WorkDebug):
    @classmethod
    def from_merged_work(cls, work: RedirectedMergedWork) -> "RedirectedWorkDebug":
        debug = WorkDebug._from_merged_work(work).model_dump()
        return RedirectedWorkDebug(**debug)


class DeletedWorkDebug(WorkDebug):
    deleted_reason: DeletedReason

    @classmethod
    def from_merged_work(cls, work: DeletedMergedWork) -> "DeletedWorkDebug":
        debug = WorkDebug._from_merged_work(work).model_dump()
        return DeletedWorkDebug(**debug, deleted_reason=work.deleted_reason)
