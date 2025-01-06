from sources.gzip_source import GZipSource
from transformers.base_transformer import BaseTransformer

from collections.abc import Generator
from models.graph_node import SourceName
from models.graph_edge import BaseEdge
from .raw_concept import RawLibraryOfCongressConcept


class LibraryOfCongressNamesTransformer(BaseTransformer):
    def __init__(self, url: str):
        self.source = GZipSource(url)

    def transform_node(self, raw_node: dict) -> SourceName | None:
        raw_concept = RawLibraryOfCongressConcept(raw_node)

        if raw_concept.exclude() or raw_concept.is_geographic:
            return None

        return SourceName(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
        )

    def extract_edges(self, raw_node: dict) -> Generator[BaseEdge]:
        # At the moment there are no edges to extract. Return an empty generator.
        yield from ()
