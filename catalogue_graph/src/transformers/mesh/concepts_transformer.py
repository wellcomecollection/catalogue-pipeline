from collections.abc import Generator

from models.graph_edge import SourceConceptHasParent, SourceConceptRelatedTo
from models.graph_node import SourceConcept
from sources.mesh.concepts_source import MeSHConceptsSource, RawMeshNode
from transformers.base_transformer import BaseTransformer

from .raw_concept import RawMeSHConcept


class MeSHConceptsTransformer(BaseTransformer):
    def __init__(self, url: str):
        super().__init__()
        self.source = MeSHConceptsSource(url)

    def transform_node(self, raw_node: RawMeshNode) -> SourceConcept | None:
        raw_concept = RawMeSHConcept(raw_node)

        if raw_concept.is_geographic:
            return None

        return SourceConcept(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
            alternative_ids=raw_concept.alternative_ids,
            description=raw_concept.description,
        )

    def extract_edges(
        self, raw_node: RawMeshNode
    ) -> Generator[SourceConceptHasParent | SourceConceptRelatedTo]:
        raw_concept = RawMeSHConcept(raw_node)

        if raw_concept.is_geographic:
            return

        for parent_id in raw_concept.parent_concept_ids:
            yield SourceConceptHasParent(from_id=raw_concept.source_id, to_id=parent_id)

        # RELATED_TO edges are undirected, but we are only creating one edge here to avoid
        # duplicates due to both MeSH concepts referencing each other in the source data
        for related_id in raw_concept.related_concept_ids:
            yield SourceConceptRelatedTo(
                from_id=raw_concept.source_id, to_id=related_id
            )
