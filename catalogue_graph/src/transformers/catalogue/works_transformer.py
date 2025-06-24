from collections.abc import Generator
from typing import Tuple

from models.graph_edge import (
    WorkHasConcept,
    WorkHasConceptAttributes,
    WorkHasIdentifier,
    WorkHasIdentifierAttributes,
)
from models.graph_node import Work
from sources.elasticsearch_source import ElasticsearchSource

from transformers.base_transformer import BaseTransformer

from .raw_work import RawCatalogueWork


class CatalogueWorksTransformer(BaseTransformer):
    def __init__(self, url: str, index_name: str, basic_auth: Tuple[str, str]):
        self.source = ElasticsearchSource(url, index_name, basic_auth)

    def transform_node(self, raw_node: dict) -> Work:
        raw_work = RawCatalogueWork(raw_node)

        return Work(
            id=raw_work.wellcome_id,
            label=raw_work.label,
            alternative_labels=raw_work.alternative_labels,
            type=raw_work.type,
            lettering=raw_work.lettering,
            reference_number=raw_work.reference_number,
            description=raw_work.description,
            physical_description=raw_work.physical_description,
            edition=raw_work.edition,
            duration=raw_work.duration,
            current_frequency=raw_work.current_frequency,
            former_frequency=raw_work.former_frequency,
            designation=raw_work.designation,
            format_id=raw_work.format_id,
            format_label=raw_work.format_label,
            collection_path_label=raw_work.collection_path_label,
            other_identifiers=raw_work.other_identifiers,
            created_date=raw_work.created_date,
            thumbnail=raw_work.thumbnail,
            production=raw_work.production,
            languages=raw_work.languages,
            notes=raw_work.notes,
            items=raw_work.items,
            holdings=raw_work.holdings,
            image_data=raw_work.image_data,
        )

    def extract_edges(
        self, raw_node: dict
    ) -> Generator[WorkHasConcept | WorkHasIdentifier]:
        raw_work = RawCatalogueWork(raw_node)

        for identifier in raw_work.identifiers:
            attributes = WorkHasIdentifierAttributes(
                referenced_in=identifier["referenced_in"],
            )
            yield WorkHasIdentifier(
                from_id=raw_work.wellcome_id,
                to_id=identifier["id"],
                attributes=attributes,
            )

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
