from collections.abc import Generator

from models.graph_edge import (
    SourceConceptNarrowerThan,
    SourceConceptRelatedTo,
)
from models.graph_node import SourceConcept
from sources.gzip_source import GZipSource
from transformers.base_transformer import BaseTransformer
from transformers.loc.raw_concept import RawLibraryOfCongressConcept


class LibraryOfCongressConceptsTransformer(BaseTransformer):
    def __init__(self, url: str):
        super().__init__()
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

    def extract_edges(
        self, raw_node: dict
    ) -> Generator[SourceConceptNarrowerThan | SourceConceptRelatedTo]:
        raw_concept = RawLibraryOfCongressConcept(raw_node)

        if raw_concept.exclude() or raw_concept.is_geographic:
            return

        for broader_id in raw_concept.broader_concept_ids:
            yield SourceConceptNarrowerThan(
                from_id=raw_concept.source_id, to_id=broader_id
            )

        for narrower_id in raw_concept.narrower_concept_ids:
            yield SourceConceptNarrowerThan(
                from_id=narrower_id, to_id=raw_concept.source_id
            )

        for related_id in raw_concept.related_concept_ids:
            yield SourceConceptRelatedTo(
                from_id=raw_concept.source_id, to_id=related_id
            )
            yield SourceConceptRelatedTo(
                from_id=related_id, to_id=raw_concept.source_id
            )
