from collections.abc import Generator

from graph.sources.wikidata.linked_ontology_source import (
    HAS_PARENT_EDGE_TYPES,
)
from graph.sources.wikidata.sparql_query_builder import WikidataEdgeQueryType
from models.graph_edge import (
    BaseEdge,
    SourceConceptHasFieldOfWork,
    SourceConceptHasFounder,
    SourceConceptHasParent,
    SourceConceptSameAs,
    SourceConceptSameAsAttributes,
)
from models.graph_node import SourceConcept

from .base_transformer import WikidataBaseTransformer
from .raw_concept import RawWikidataConcept


class WikidataConceptsTransformer(WikidataBaseTransformer):
    def transform_node(self, raw_node: dict) -> SourceConcept | None:
        raw_concept = RawWikidataConcept(raw_node)

        if raw_concept.exclude():
            return None

        return SourceConcept(
            id=raw_concept.source_id,
            label=raw_concept.label,
            source=raw_concept.source,
            alternative_labels=raw_concept.alternative_labels,
            description=raw_concept.description,
        )

    def extract_edges(
        self, raw_edge: tuple[dict, WikidataEdgeQueryType]
    ) -> Generator[BaseEdge]:
        edge, edge_type = raw_edge
        if edge_type in ("same_as_loc", "same_as_mesh"):
            edge_attributes = SourceConceptSameAsAttributes(source="wikidata")
            yield SourceConceptSameAs(
                from_id=edge["from_id"],
                to_id=edge["to_id"],
                attributes=edge_attributes,
            )
            yield SourceConceptSameAs(
                from_id=edge["to_id"],
                to_id=edge["from_id"],
                attributes=edge_attributes,
            )
        elif edge_type in HAS_PARENT_EDGE_TYPES:
            yield SourceConceptHasParent(
                from_id=edge["from_id"],
                to_id=edge["to_id"],
            )
        elif edge_type == "has_founder":
            yield SourceConceptHasFounder(
                from_id=edge["from_id"],
                to_id=edge["to_id"],
            )
        elif edge_type in ("has_industry", "has_field_of_work"):
            yield SourceConceptHasFieldOfWork(
                from_id=edge["from_id"],
                to_id=edge["to_id"],
            )
        else:
            raise ValueError(f"Unknown edge type {edge_type}")
