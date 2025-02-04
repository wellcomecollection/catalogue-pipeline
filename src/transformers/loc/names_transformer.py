from collections.abc import Generator

from models.graph_edge import SourceConceptRelatedTo
from models.graph_node import SourceName
from sources.gzip_source import GZipSource
from transformers.base_transformer import BaseTransformer
from transformers.loc.skos.raw_concept import RawLibraryOfCongressSKOSConcept


class LibraryOfCongressNamesTransformer(BaseTransformer):
    def __init__(self, url: str):
        self.source = GZipSource(url)

    def transform_node(self, raw_node: dict) -> SourceName | None:
        raw_concept = RawLibraryOfCongressSKOSConcept(raw_node)

        if raw_concept.exclude() or raw_concept.is_geographic:
            return None

        return SourceName(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
        )

    def extract_edges(self, raw_node: dict) -> Generator[SourceConceptRelatedTo]:
        # Although there are some broader and narrower relationships specified
        # in the MADS instance of the names export, there are not many that are likely
        # to be relevant, so the names transformer uses the lighter-weight SKOS export.
        raw_concept = RawLibraryOfCongressSKOSConcept(raw_node)

        if raw_concept.exclude() or not raw_concept.is_geographic:
            return

        for related_id in raw_concept.related_concept_ids:
            yield SourceConceptRelatedTo(
                from_id=raw_concept.source_id, to_id=related_id
            )
            yield SourceConceptRelatedTo(
                from_id=related_id, to_id=raw_concept.source_id
            )
