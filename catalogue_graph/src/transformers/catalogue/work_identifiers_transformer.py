from collections.abc import Generator
from typing import Tuple

from models.graph_edge import WorkIdentifierHasParent
from models.graph_node import WorkIdentifier
from sources.catalogue.work_identifiers_source import CatalogueWorkIdentifiersSource

from transformers.base_transformer import BaseTransformer

from .raw_work_identifier import RawCatalogueWorkIdentifier


class CatalogueWorkIdentifiersTransformer(BaseTransformer):
    def __init__(self, url: str, index_name: str, basic_auth: Tuple[str, str]):
        self.source = CatalogueWorkIdentifiersSource(url, index_name, basic_auth)

    def transform_node(self, raw_data: Tuple[dict, str]) -> WorkIdentifier:
        raw_identifier = RawCatalogueWorkIdentifier(raw_data[0], raw_data[1])

        return WorkIdentifier(
            id=raw_identifier.unique_id,
            identifier=raw_identifier.identifier,
            label=raw_identifier.identifier_type,
        )

    def extract_edges(
        self, raw_data: Tuple[dict, str]
    ) -> Generator[WorkIdentifierHasParent]:
        raw_identifier = RawCatalogueWorkIdentifier(raw_data[0], raw_data[1])

        if raw_identifier.parent:
            yield WorkIdentifierHasParent(
                from_id=raw_identifier.unique_id, to_id=raw_identifier.parent
            )
