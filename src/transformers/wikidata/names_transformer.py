from models.graph_node import SourceName
from sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource
from .concepts_transformer import WikidataConceptsTransformer
from transformers.base_transformer import EntityType

from .raw_concept import RawWikidataName


class WikidataNamesTransformer(WikidataConceptsTransformer):
    def __init__(self, entity_type: EntityType):
        self.source = WikidataLinkedOntologySource("names", "loc", entity_type)

    def transform_node(self, raw_node: dict) -> SourceName | None:
        raw_concept = RawWikidataName(raw_node)

        return SourceName(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
            description=raw_concept.description,
            date_of_birth=raw_concept.date_of_birth,
            date_of_death=raw_concept.date_of_death,
            place_of_birth=raw_concept.place_of_birth,
        )
