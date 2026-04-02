from graph.sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource
from models.events import ExtractorEvent
from models.graph_node import SourceLocation
from utils.types import TransformerType

from .concepts_transformer import WikidataConceptsTransformer
from .raw_concept import RawWikidataLocation


class WikidataLocationsTransformer(WikidataConceptsTransformer):
    def __init__(self, linked_transformer: TransformerType, event: ExtractorEvent):
        self.source = WikidataLinkedOntologySource(linked_transformer, event)

    def transform_node(self, raw_node: dict) -> SourceLocation | None:
        raw_concept = RawWikidataLocation(raw_node)

        source_concept = super().transform_node(raw_node)
        if not source_concept:
            return None

        return SourceLocation(
            **source_concept.model_dump(),
            latitude=raw_concept.coordinates["latitude"],
            longitude=raw_concept.coordinates["longitude"],
        )
