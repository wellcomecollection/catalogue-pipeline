from models.events import EntityType
from models.graph_node import SourceLocation
from sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource
from utils.types import TransformerType

from .concepts_transformer import WikidataConceptsTransformer
from .raw_concept import RawWikidataLocation


class WikidataLocationsTransformer(WikidataConceptsTransformer):
    def __init__(
        self,
        linked_transformer: TransformerType,
        entity_type: EntityType,
        pipeline_date: str,
    ):
        super().__init__(linked_transformer, entity_type, pipeline_date)
        self.source = WikidataLinkedOntologySource(
            linked_transformer, entity_type, pipeline_date
        )

    def transform_node(self, raw_node: dict) -> SourceLocation | None:
        raw_concept = RawWikidataLocation(raw_node)

        return SourceLocation(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
            description=raw_concept.description,
            latitude=raw_concept.coordinates["latitude"],
            longitude=raw_concept.coordinates["longitude"],
        )
