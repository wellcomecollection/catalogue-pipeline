from pydantic import BaseModel

from ingestor.models.aggregate.work import WorkAggregatableValues
from ingestor.models.display.work import DisplayWork
from ingestor.models.filter.work import WorkFilterableValues
from ingestor.models.query.work import QueryWork
from ingestor.models.indexable import IndexableRecord


class IndexableWork(IndexableRecord):
    query: QueryWork
    display: DisplayWork
    aggregatableValues: WorkAggregatableValues
    filterableValues: WorkFilterableValues

    def get_id(self): return self.query.id
