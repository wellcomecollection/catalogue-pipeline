from collections.abc import Generator

from models.events import EntityType
from models.graph_edge import (
    BaseEdge,
    SourceConceptHasFieldOfWork,
    SourceConceptHasIndustry,
    SourceConceptHasParent,
    SourceConceptSameAs,
    SourceConceptSameAsAttributes,
    SourceNameRelatedTo,
    SourceNameRelatedToAttributes,
)
from models.graph_node import SourceName
from sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource
from utils.types import TransformerType

from .concepts_transformer import WikidataConceptsTransformer
from .raw_concept import RawWikidataName


class WikidataNamesTransformer(WikidataConceptsTransformer):
    def __init__(
        self,
        linked_transformer: TransformerType,
        entity_type: EntityType,
        pipeline_date: str,
    ):
        self.source = WikidataLinkedOntologySource(
            linked_transformer, entity_type, pipeline_date
        )

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

    def extract_edges(self, raw_node: dict) -> Generator[BaseEdge]:
        if raw_node["type"] == "SAME_AS":
            edge_attributes = SourceConceptSameAsAttributes(source="wikidata")
            yield SourceConceptSameAs(
                from_id=raw_node["from_id"],
                to_id=raw_node["to_id"],
                attributes=edge_attributes,
            )
            yield SourceConceptSameAs(
                from_id=raw_node["to_id"],
                to_id=raw_node["from_id"],
                attributes=edge_attributes,
            )
        elif raw_node["type"] == "HAS_PARENT":
            yield SourceConceptHasParent(
                from_id=raw_node["from_id"],
                to_id=raw_node["to_id"],
            )
        elif raw_node["type"] == "HAS_INDUSTRY":
            yield SourceConceptHasIndustry(
                from_id=raw_node["from_id"],
                to_id=raw_node["to_id"],
            )
        if raw_node["type"] == "HAS_FIELD_OF_WORK":
            yield SourceConceptHasFieldOfWork(
                from_id=raw_node["from_id"],
                to_id=raw_node["to_id"],
            )
        elif raw_node["type"] == "RELATED_TO":
            attributes = SourceNameRelatedToAttributes(
                relationship_type=raw_node["subtype"]
            )
            yield SourceNameRelatedTo(
                from_id=raw_node["from_id"],
                to_id=raw_node["to_id"],
                attributes=attributes,
            )
        else:
            raise ValueError(f"Unknown edge type {raw_node['type']}")
