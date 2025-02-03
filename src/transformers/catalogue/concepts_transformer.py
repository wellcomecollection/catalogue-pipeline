from collections.abc import Generator

from models.graph_edge import BaseEdge
from models.graph_node import Concept
from sources.catalogue.concepts_source import CatalogueConceptsSource
from transformers.base_transformer import BaseTransformer

from .raw_concept import RawCatalogueConcept


class CatalogueConceptsTransformer(BaseTransformer):
    def __init__(self, url: str):
        self.source = CatalogueConceptsSource(url)

    def transform_node(self, raw_node: dict) -> Concept | None:
        raw_concept = RawCatalogueConcept(raw_node)

        if not raw_concept.is_concept:
            return None

        return Concept(
            id=raw_concept.wellcome_id,
            label=raw_concept.label,
            source=raw_concept.source,
            type=raw_concept.type,
        )

    def extract_edges(self, raw_node: RawCatalogueConcept) -> Generator[BaseEdge]:
        # TODO: Extract `HAS_SOURCE_CONCEPT` edges
        yield from ()
