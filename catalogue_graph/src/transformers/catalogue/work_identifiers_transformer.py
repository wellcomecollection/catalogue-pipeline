from collections.abc import Generator

from models.events import BasePipelineEvent
from models.graph_edge import (
    PathIdentifierHasParent,
    WorkHasPathIdentifier,
)
from models.graph_node import PathIdentifier
from sources.merged_works_source import MergedWorksSource
from transformers.base_transformer import BaseTransformer
from utils.elasticsearch import ElasticsearchMode

from .raw_work import RawCatalogueWork

ES_FIELDS = [
    "state.canonicalId",
    "state.sourceIdentifier",
    "data.collectionPath",
    "data.otherIdentifiers",
]

# We are only interested in visible works with a non-null collection path
ES_QUERY = {
    "bool": {
        "must": [
            {"match": {"type": "Visible"}},
            {"exists": {"field": "data.collectionPath.path"}},
        ]
    }
}


class CatalogueWorkIdentifiersTransformer(BaseTransformer):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_mode: ElasticsearchMode,
    ) -> None:
        super().__init__()
        self.source = MergedWorksSource(
            event, query=ES_QUERY, fields=ES_FIELDS, es_mode=es_mode
        )

    def transform_node(self, raw_data: dict) -> PathIdentifier | None:
        raw_work = RawCatalogueWork(raw_data)

        if raw_work.path_identifier is not None:
            return PathIdentifier(
                id=raw_work.path_identifier,
                label=None,
            )

        return None

    def extract_edges(
        self, raw_data: dict
    ) -> Generator[WorkHasPathIdentifier | PathIdentifierHasParent]:
        raw_work = RawCatalogueWork(raw_data)

        if raw_work.path_identifier is None:
            return

        yield WorkHasPathIdentifier(
            from_id=raw_work.wellcome_id,
            to_id=raw_work.path_identifier,
        )

        if raw_work.parent_path_identifier is not None:
            yield PathIdentifierHasParent(
                from_id=raw_work.path_identifier,
                to_id=raw_work.parent_path_identifier,
            )
