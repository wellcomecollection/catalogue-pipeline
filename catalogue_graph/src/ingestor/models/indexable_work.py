from ingestor.models.aggregate.work import WorkAggregatableValues
from ingestor.models.debug.work import (
    DeletedWorkDebug,
    InvisibleWorkDebug,
    RedirectedWorkDebug,
    VisibleWorkDebug,
    WorkDebug,
)
from ingestor.models.denormalised.work import DenormalisedWork
from ingestor.models.display.work import DisplayWork
from ingestor.models.filter.work import WorkFilterableValues
from ingestor.models.indexable import IndexableRecord
from ingestor.models.query.work import QueryWork
from ingestor.models.shared.identifier import Identifiers
from utils.types import WorkStatus


class IndexableWork(IndexableRecord):
    type: WorkStatus
    debug: WorkDebug

    def get_id(self) -> str:
        return self.debug.source.id


class VisibleIndexableWork(IndexableWork):
    query: QueryWork
    display: DisplayWork
    aggregatable_values: WorkAggregatableValues
    filterable_values: WorkFilterableValues
    debug: VisibleWorkDebug


class InvisibleIndexableWork(IndexableWork):
    debug: InvisibleWorkDebug

    @staticmethod
    def from_denormalised_work(work: DenormalisedWork):
        return InvisibleIndexableWork(
            type=work.type, debug=InvisibleWorkDebug.from_denormalised_work(work)
        )


class RedirectedIndexableWork(IndexableWork):
    debug: RedirectedWorkDebug
    redirect_target: Identifiers

    @staticmethod
    def from_denormalised_work(work: DenormalisedWork):
        return RedirectedIndexableWork(
            type=work.type,
            debug=RedirectedWorkDebug.from_denormalised_work(work),
            redirect_target=work.redirect_target,
        )


class DeletedIndexableWork(IndexableWork):
    debug: DeletedWorkDebug

    @staticmethod
    def from_denormalised_work(work: DenormalisedWork):
        return DeletedIndexableWork(
            type=work.type, debug=DeletedWorkDebug.from_denormalised_work(work)
        )
