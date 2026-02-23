from collections.abc import Generator

from elasticsearch import Elasticsearch

from models.events import BasePipelineEvent
from models.graph_edge import WorkHasConcept, WorkHasConceptAttributes
from models.graph_node import Work
from sources.merged_works_source import MergedWorksSource
from transformers.base_transformer import BaseTransformer

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
    "data.referenceNumber",
    "data.collectionPath",
]


class CatalogueWorksTransformer(BaseTransformer):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
    ):
        self.source = MergedWorksSource(
            event, query=ES_QUERY, fields=ES_FIELDS, es_client=es_client
        )

    def transform_node(self, raw_node: dict) -> Work:
        raw_work = RawCatalogueWork(raw_node)
        return Work(
            id=raw_work.wellcome_id,
            label=raw_work.label,
            alternative_labels=raw_work.alternative_labels,
            type=raw_work.type,
            reference_number=raw_work.reference_number,
            collection_path=raw_work.raw_path,
            collection_path_label=raw_work.path_label,
        )

    def extract_edges(self, raw_node: dict) -> Generator[WorkHasConcept]:
        raw_work = RawCatalogueWork(raw_node)

        for concept in raw_work.concepts:
            attributes = WorkHasConceptAttributes(
                referenced_in=concept.referenced_in,
                referenced_type=concept.referenced_type,
            )
            yield WorkHasConcept(
                from_id=raw_work.wellcome_id,
                to_id=concept.id,
                attributes=attributes,
            )
