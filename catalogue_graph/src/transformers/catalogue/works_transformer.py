from collections.abc import Generator

import config
from models.graph_edge import WorkHasConcept, WorkHasConceptAttributes
from models.graph_node import Work
from sources.elasticsearch_source import ElasticsearchSource
from transformers.base_transformer import BaseTransformer
from utils.elasticsearch import get_standard_index_name

from .raw_work import RawCatalogueWork

# Only store visible works in the graph
ES_QUERY = {"match": {"type": "Visible"}}
ES_FIELDS = [
    "type",
    "data.title",
    "data.alternativeTitles",
    "data.workType",
    "state.canonicalId",
    "data.subjects",
    "data.contributors",
    "data.genres",
]


class CatalogueWorksTransformer(BaseTransformer):
    def __init__(self, pipeline_date: str | None, is_local: bool):
        index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, pipeline_date
        )
        self.source = ElasticsearchSource(
            pipeline_date, is_local, index_name, ES_QUERY, ES_FIELDS
        )

    def transform_node(self, raw_node: dict) -> Work:
        raw_work = RawCatalogueWork(raw_node)
        return Work(
            id=raw_work.wellcome_id,
            label=raw_work.label,
            alternative_labels=raw_work.alternative_labels,
            type=raw_work.type,
        )

    def extract_edges(self, raw_node: dict) -> Generator[WorkHasConcept]:
        raw_work = RawCatalogueWork(raw_node)

        for concept in raw_work.concepts:
            attributes = WorkHasConceptAttributes(
                referenced_in=concept["referenced_in"],
                referenced_type=concept["referenced_type"],
            )
            yield WorkHasConcept(
                from_id=raw_work.wellcome_id,
                to_id=concept["id"],
                attributes=attributes,
            )
