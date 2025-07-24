from collections.abc import Generator

from models.graph_edge import (
    PathIdentifierHasParent,
    WorkHasPathIdentifier,
)
from models.graph_node import PathIdentifier
from sources.elasticsearch_source import ElasticsearchSource

from transformers.base_transformer import BaseTransformer

from .raw_work import RawCatalogueWork

ES_FIELDS = [
    "state.canonicalId",
    "state.sourceIdentifier",
    "data.collectionPath",
    "data.otherIdentifiers",
]
ES_QUERY = {
    "bool": {
        "must": [
            {"match": {"type": "Visible"}},
            {"exists": {"field": "data.collectionPath.path"}},
        ]
    }
}


class CatalogueWorkIdentifiersTransformer(BaseTransformer):
    def __init__(self, pipeline_date: str | None, is_local: bool) -> None:
        self.source = ElasticsearchSource(pipeline_date, is_local, ES_QUERY, ES_FIELDS)

    def transform_node(self, raw_data: dict) -> PathIdentifier | None:
        raw_identifier = RawCatalogueWork(raw_data)

        if raw_identifier.path_identifier is not None:
            return PathIdentifier(
                id=raw_identifier.path_identifier,
                label=None,
            )

        return None

    def extract_edges(
        self, raw_data: dict
    ) -> Generator[WorkHasPathIdentifier | PathIdentifierHasParent]:
        raw_identifier = RawCatalogueWork(raw_data)

        if raw_identifier.path_identifier is None:
            return

        yield WorkHasPathIdentifier(
            from_id=raw_identifier.wellcome_id,
            to_id=raw_identifier.path_identifier,
        )

        if raw_identifier.parent_path_identifier is not None:
            yield PathIdentifierHasParent(
                from_id=raw_identifier.path_identifier,
                to_id=raw_identifier.parent_path_identifier,
            )
