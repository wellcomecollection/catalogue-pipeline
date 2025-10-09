from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.models.aggregate.work import WorkAggregatableValues
from ingestor.models.debug.work import (
    DeletedWorkDebug,
    InvisibleWorkDebug,
    RedirectedWorkDebug,
    VisibleWorkDebug,
    WorkDebug,
)
from ingestor.models.display.work import DisplayWork
from ingestor.models.filter.work import WorkFilterableValues
from ingestor.models.indexable import IndexableRecord
from ingestor.models.merged.work import (
    DeletedMergedWork,
    InvisibleMergedWork,
    RedirectedMergedWork,
)
from ingestor.models.query.work import QueryWork
from ingestor.models.shared.identifier import Identifiers
from utils.types import WorkStatus


class IndexableWork(IndexableRecord):
    type: WorkStatus
    debug: WorkDebug

    def get_id(self) -> str:
        return self.debug.source.id

    @staticmethod
    def from_raw_document(work: dict) -> "IndexableWork":
        if work["type"] == "Visible":
            return VisibleIndexableWork.model_validate(work)
        if work["type"] == "Invisible":
            return InvisibleIndexableWork.model_validate(work)
        if work["type"] == "Redirected":
            return RedirectedIndexableWork.model_validate(work)
        if work["type"] == "Deleted":
            return DeletedIndexableWork.model_validate(work)

        raise ValueError(f"Unknown work type '{work['type']}' for work {work}")


class VisibleIndexableWork(IndexableWork):
    query: QueryWork
    display: DisplayWork
    aggregatable_values: WorkAggregatableValues
    filterable_values: WorkFilterableValues
    debug: VisibleWorkDebug
    type: WorkStatus = "Visible"

    @classmethod
    def from_extracted_work(
        cls, extracted: VisibleExtractedWork
    ) -> "VisibleIndexableWork":
        return VisibleIndexableWork(
            query=QueryWork.from_extracted_work(extracted),
            display=DisplayWork.from_extracted_work(extracted),
            aggregatable_values=WorkAggregatableValues.from_extracted_work(extracted),
            filterable_values=WorkFilterableValues.from_extracted_work(extracted),
            debug=VisibleWorkDebug.from_merged_work(extracted.work),
        )


class InvisibleIndexableWork(IndexableWork):
    debug: InvisibleWorkDebug
    type: WorkStatus = "Invisible"

    @classmethod
    def from_merged_work(cls, work: InvisibleMergedWork) -> "InvisibleIndexableWork":
        return InvisibleIndexableWork(debug=InvisibleWorkDebug.from_merged_work(work))


class RedirectedIndexableWork(IndexableWork):
    debug: RedirectedWorkDebug
    redirect_target: Identifiers
    type: WorkStatus = "Redirected"

    @classmethod
    def from_merged_work(cls, work: RedirectedMergedWork) -> "RedirectedIndexableWork":
        return RedirectedIndexableWork(
            debug=RedirectedWorkDebug.from_merged_work(work),
            redirect_target=work.redirect_target,
        )


class DeletedIndexableWork(IndexableWork):
    debug: DeletedWorkDebug
    type: WorkStatus = "Deleted"

    @classmethod
    def from_merged_work(cls, work: DeletedMergedWork) -> "DeletedIndexableWork":
        return DeletedIndexableWork(debug=DeletedWorkDebug.from_merged_work(work))
