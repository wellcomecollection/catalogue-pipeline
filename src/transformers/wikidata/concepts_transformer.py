from collections.abc import Generator

from models.graph_edge import SourceConceptSameAs
from models.graph_node import SourceConcept
from sources.wikidata.linked_ontology_source import (
    OntologyType,
    WikidataLinkedOntologySource,
)
from transformers.base_transformer import BaseTransformer, EntityType

from .raw_concept import RawWikidataConcept


class WikidataConceptsTransformer(BaseTransformer):
    def __init__(self, entity_type: EntityType, linked_ontology: OntologyType):
        self.source = WikidataLinkedOntologySource(
            "concepts", linked_ontology, entity_type
        )

    def transform_node(self, raw_node: dict) -> SourceConcept | None:
        raw_concept = RawWikidataConcept(raw_node)

        return SourceConcept(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
            description=raw_concept.description,
        )

    def extract_edges(self, raw_edge: dict) -> Generator[SourceConceptSameAs]:
        linked_id, wikidata_id = raw_edge["linked_id"], raw_edge["wikidata_id"]
        edge_attributes = {"source": "wikidata"}

        yield SourceConceptSameAs(
            from_id=linked_id, to_id=wikidata_id, attributes=edge_attributes
        )
        yield SourceConceptSameAs(
            from_id=wikidata_id, to_id=linked_id, attributes=edge_attributes
        )
