from graph.sources.wikidata.linked_ontology_edge_source import (
    WikidataLinkedOntologyEdgeSource,
)
from graph.sources.wikidata.linked_ontology_node_source import (
    WikidataLinkedOntologyNodeSource,
)
from graph.transformers.graph_transformer import GraphBaseTransformer
from models.events import ExtractorEvent
from utils.types import TransformerType


class WikidataBaseTransformer(GraphBaseTransformer):
    def __init__(self, linked_transformer: TransformerType, event: ExtractorEvent):
        super().__init__()
        if event.entity_type == "nodes":
            self.source = WikidataLinkedOntologyNodeSource(linked_transformer, event)
        else:
            self.source = WikidataLinkedOntologyEdgeSource(linked_transformer, event)
