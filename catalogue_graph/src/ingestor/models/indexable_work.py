from abc import ABC

from ingestor.extractors.works_extractor import ExtractedWork, VisibleExtractedWork
from ingestor.models.aggregate.work import WorkAggregatableValues
from ingestor.models.debug.work import (
    DeletedWorkDebug,
    InvisibleWorkDebug,
    RedirectedWorkDebug,
    VisibleWorkDebug,
    WorkDebug,
)
from ingestor.models.denormalised.work import (
    DeletedDenormalisedWork,
    InvisibleDenormalisedWork,
    RedirectedDenormalisedWork,
)
from ingestor.models.display.work import DisplayWork
from ingestor.models.filter.work import WorkFilterableValues
from ingestor.models.indexable import IndexableRecord
from ingestor.models.query.work import QueryWork
from ingestor.models.shared.identifier import Identifiers
from utils.types import WorkStatus


class IndexableWork(IndexableRecord, ABC):
    type: WorkStatus
    debug: WorkDebug

    def get_id(self) -> str:
        return self.debug.source.id

    @classmethod
    def from_extracted_work(cls, extracted: ExtractedWork) -> "IndexableWork":
        work = extracted.work
        base = IndexableWork(
            type=work.type, debug=WorkDebug.from_denormalised_work(work)
        ).model_dump()

        if isinstance(extracted, VisibleExtractedWork):
            return VisibleIndexableWork(
                **base,
                query=QueryWork.from_extracted_work(extracted),
                display=DisplayWork.from_extracted_work(extracted),
                aggregatable_values=WorkAggregatableValues.from_extracted_work(
                    extracted
                ),
                filterable_values=WorkFilterableValues.from_extracted_work(extracted),
            )
        if isinstance(work, RedirectedDenormalisedWork):
            return RedirectedIndexableWork(**base, redirect_target=work.redirect_target)
        if isinstance(work, DeletedDenormalisedWork):
            return DeletedIndexableWork(**base)
        if isinstance(work, InvisibleDenormalisedWork):
            return InvisibleIndexableWork(**base)

        raise TypeError(f"Unknown work type '{type(work)}' for work {work}")


class VisibleIndexableWork(IndexableWork):
    query: QueryWork
    display: DisplayWork
    aggregatable_values: WorkAggregatableValues
    filterable_values: WorkFilterableValues
    debug: VisibleWorkDebug


class InvisibleIndexableWork(IndexableWork):
    debug: InvisibleWorkDebug


class RedirectedIndexableWork(IndexableWork):
    debug: RedirectedWorkDebug
    redirect_target: Identifiers


class DeletedIndexableWork(IndexableWork):
    debug: DeletedWorkDebug
