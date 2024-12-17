from sources.gzip_source import GZipSource
from transformers.base_transformer import BaseTransformer
from collections.abc import Generator
from models.graph_node import SourceConcept
from models.graph_edge import SourceConceptNarrowerThan
from .raw_concept import RawLibraryOfCongressConcept


class LibraryOfCongressConceptsTransformer(BaseTransformer):
    def __init__(self, url: str):
        self.source = GZipSource(url)

    def transform_node(self, raw_node: dict) -> SourceConcept | None:
        raw_concept = RawLibraryOfCongressConcept(raw_node)

        if raw_concept.exclude() or raw_concept.is_geographic:
            return None

        return SourceConcept(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
        )

    def extract_edges(self, raw_node: dict) -> Generator[SourceConceptNarrowerThan]:
        raw_concept = RawLibraryOfCongressConcept(raw_node)

        if raw_concept.exclude() or raw_concept.is_geographic:
            return []

        for broader_id in raw_concept.broader_concept_ids:
            yield SourceConceptNarrowerThan(from_id=self.source_id, to_id=broader_id)
