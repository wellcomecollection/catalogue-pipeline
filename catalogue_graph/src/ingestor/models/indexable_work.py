from ingestor.models.aggregate.work import WorkAggregatableValues
from ingestor.models.debug.work import WorkDebug
from ingestor.models.display.work import DisplayWork
from ingestor.models.filter.work import WorkFilterableValues
from ingestor.models.indexable import IndexableRecord
from ingestor.models.query.work import QueryWork
from utils.types import WorkStatus


class IndexableWork(IndexableRecord):
    query: QueryWork
    display: DisplayWork
    aggregatableValues: WorkAggregatableValues
    filterableValues: WorkFilterableValues
    type: WorkStatus
    debug: WorkDebug

    def get_id(self) -> str:
        return self.query.id
