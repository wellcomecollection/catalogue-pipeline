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
    def _from_denormalised_work(cls, work: DenormalisedWork) -> "WorkDebug":
        return WorkDebug(
            source=SourceWorkDebugInformation(
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
    def from_denormalised_work(
        cls, work: VisibleDenormalisedWork
    ) -> "VisibleWorkDebug":
        debug = WorkDebug._from_denormalised_work(work).model_dump()
        return VisibleWorkDebug(**debug, redirect_sources=work.redirect_sources)


class InvisibleWorkDebug(WorkDebug):
    invisibility_reasons: list[InvisibleReason]

    @classmethod
    def from_denormalised_work(
        cls, work: InvisibleDenormalisedWork
    ) -> "InvisibleWorkDebug":
        debug = WorkDebug._from_denormalised_work(work).model_dump()
        return InvisibleWorkDebug(
            **debug, invisibility_reasons=work.invisibility_reasons
        )


class RedirectedWorkDebug(WorkDebug):
    @classmethod
    def from_denormalised_work(
        cls, work: RedirectedDenormalisedWork
    ) -> "RedirectedWorkDebug":
        debug = WorkDebug._from_denormalised_work(work).model_dump()
        return RedirectedWorkDebug(**debug)


class DeletedWorkDebug(WorkDebug):
    deleted_reason: DeletedReason

    @classmethod
    def from_denormalised_work(
        cls, work: DeletedDenormalisedWork
    ) -> "DeletedWorkDebug":
        debug = WorkDebug._from_denormalised_work(work).model_dump()
        return DeletedWorkDebug(**debug, deleted_reason=work.deleted_reason)
