from collections.abc import Generator

import config
from models.graph_edge import WorkIdentifierHasParent
from models.graph_node import WorkIdentifier
from sources.catalogue.work_identifiers_source import (
    CatalogueWorkIdentifiersSource,
    RawDenormalisedWorkIdentifier,
)
from utils.elasticsearch import get_standard_index_name

from transformers.base_transformer import BaseTransformer

from .raw_work_identifier import RawCatalogueWorkIdentifier
from .works_transformer import ES_QUERY

ES_FIELDS = [
    "state.canonicalId",
    "state.sourceIdentifier",
    "data.collectionPath",
    "data.otherIdentifiers",
]


class CatalogueWorkIdentifiersTransformer(BaseTransformer):
    def __init__(self, pipeline_date: str | None, is_local: bool):
        index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, pipeline_date
        )
        self.source = CatalogueWorkIdentifiersSource(
            pipeline_date, is_local, index_name, ES_QUERY, ES_FIELDS
        )
        self.streamed_ids = set()

    def transform_node(
        self, raw_data: RawDenormalisedWorkIdentifier
    ) -> WorkIdentifier | None:
        raw_identifier = RawCatalogueWorkIdentifier(raw_data)

        if raw_identifier.unique_id not in self.streamed_ids:
            self.streamed_ids.add(raw_identifier.unique_id)

            return WorkIdentifier(
                id=raw_identifier.unique_id,
                identifier=raw_identifier.identifier,
                label=raw_identifier.identifier_type,
            )

    def extract_edges(
        self, raw_data: RawDenormalisedWorkIdentifier
    ) -> Generator[WorkIdentifierHasParent]:
        raw_identifier = RawCatalogueWorkIdentifier(raw_data)

        if raw_identifier.parent and raw_identifier.unique_id not in self.streamed_ids:
            self.streamed_ids.add(raw_identifier.unique_id)
            yield WorkIdentifierHasParent(
                from_id=raw_identifier.unique_id, to_id=raw_identifier.parent
            )
