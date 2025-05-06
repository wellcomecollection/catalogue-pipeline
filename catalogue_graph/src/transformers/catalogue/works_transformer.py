from collections.abc import Generator

from models.graph_edge import WorkHasConcept, WorkHasConceptAttributes
from models.graph_node import Work
from sources.gzip_source import GZipSource

from transformers.base_transformer import BaseTransformer

from .raw_work import RawCatalogueWork


class CatalogueWorksTransformer(BaseTransformer):
    def __init__(self, url: str):
        self.source = GZipSource(url)

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
                referenced_type=concept["referenced_type"]
            )
            yield WorkHasConcept(
                from_id=raw_work.wellcome_id,
                to_id=concept["id"],
                attributes=attributes,
            )
