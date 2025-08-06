from pydantic import BaseModel

from ingestor.models.display.work import DisplayWork
from ingestor.models.query.work import QueryWork


class IndexableWork(BaseModel):
    query: QueryWork
    display: DisplayWork
