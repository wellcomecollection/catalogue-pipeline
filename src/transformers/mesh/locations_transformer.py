from collections.abc import Generator
import xml.etree.ElementTree as ET

from models.graph_edge import BaseEdge
from models.graph_node import SourceConcept
from sources.mesh.concepts_source import MeSHConceptsSource
from transformers.base_transformer import BaseTransformer

from .raw_concept import RawMeSHConcept


class MeSHLocationsTransformer(BaseTransformer):
    def __init__(self, url: str):
        self.source = MeSHConceptsSource(url)

    def transform_node(self, raw_node: ET.Element) -> SourceConcept | None:
        raw_concept = RawMeSHConcept(raw_node)

        if not raw_concept.is_geographic:
            return None

        return SourceConcept(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
            alternative_ids=raw_concept.alternative_ids,
            description=raw_concept.description
        )

    def extract_edges(self, raw_node: ET.Element) -> Generator[BaseEdge]:
        """There are no edges to extract from MeSH Locations."""
        yield from ()
