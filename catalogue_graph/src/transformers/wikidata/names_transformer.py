from collections.abc import Generator

from models.events import EntityType
from models.graph_edge import (
    BaseEdge,
    SourceConceptHasFieldOfWork,
    SourceNameRelatedTo,
    SourceNameRelatedToAttributes,
)
from models.graph_node import SourceName
from sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource
from utils.types import TransformerType

from .concepts_transformer import WikidataConceptsTransformer
from .raw_concept import RawWikidataName


class WikidataNamesTransformer(WikidataConceptsTransformer):
    def __init__(self, entity_type: EntityType, linked_transformer: TransformerType):
        self.source = WikidataLinkedOntologySource(entity_type, linked_transformer)

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

    def extract_edges(self, raw_edge: dict) -> Generator[BaseEdge]:
        if raw_edge["type"] == "HAS_FIELD_OF_WORK":
            yield SourceConceptHasFieldOfWork(
                from_id=raw_edge["from_id"],
                to_id=raw_edge["to_id"],
            )
        elif raw_edge["type"] == "RELATED_TO":
            attributes = SourceNameRelatedToAttributes(
                relationship_type=raw_edge["subtype"]
            )
            yield SourceNameRelatedTo(
                from_id=raw_edge["from_id"],
                to_id=raw_edge["to_id"],
                attributes=attributes,
            )
        else:
            yield from super().extract_edges(raw_edge)
