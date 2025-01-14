from models.graph_node import SourceConcept
from sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource
from transformers.base_transformer import BaseTransformer

from .raw_concept import RawWikidataConcept


class WikidataConceptsTransformer(BaseTransformer):
    def __init__(self, entity_type):
        self.source = WikidataLinkedOntologySource("concepts", "loc", entity_type)

    def transform_node(self, raw_node: dict) -> SourceConcept | None:
        raw_concept = RawWikidataConcept(raw_node)

        return SourceConcept(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
            description=raw_concept.description,
        )
