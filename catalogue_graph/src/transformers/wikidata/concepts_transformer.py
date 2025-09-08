from collections.abc import Generator

from models.graph_edge import (
    BaseEdge,
    SourceConceptHasFounder,
    SourceConceptHasIndustry,
    SourceConceptHasParent,
    SourceConceptSameAs,
    SourceConceptSameAsAttributes,
)
from models.graph_node import SourceConcept
from sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource
from transformers.base_transformer import BaseTransformer, EntityType
from utils.types import TransformerType

from .raw_concept import RawWikidataConcept


class WikidataConceptsTransformer(BaseTransformer):
    def __init__(
        self,
        linked_transformer: TransformerType,
        entity_type: EntityType,
        pipeline_date: str,
    ):
        self.source = WikidataLinkedOntologySource(
            linked_transformer, entity_type, pipeline_date
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
        elif raw_node["type"] == "HAS_FOUNDER":
            yield SourceConceptHasFounder(
                from_id=raw_node["from_id"],
                to_id=raw_node["to_id"],
            )
        elif raw_node["type"] == "HAS_INDUSTRY":
            yield SourceConceptHasIndustry(
                from_id=raw_node["from_id"],
                to_id=raw_node["to_id"],
            )
        else:
            raise ValueError(f"Unknown edge type {raw_node['type']}")
