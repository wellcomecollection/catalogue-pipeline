from collections.abc import Generator

from graph.sources.wikidata.linked_ontology_source import (
    PEOPLE_RELATIONSHIP_EDGE_TYPES,
)
from graph.sources.wikidata.sparql_query_builder import WikidataEdgeQueryType
from models.graph_edge import (
    BaseEdge,
    SourceNameRelatedTo,
    SourceNameRelatedToAttributes,
)
from models.graph_node import SourceName

from .concepts_transformer import WikidataConceptsTransformer
from .raw_concept import RawWikidataName


class WikidataNamesTransformer(WikidataConceptsTransformer):
    def transform_node(self, raw_node: dict) -> SourceName | None:
        raw_concept = RawWikidataName(raw_node)

        source_concept = super().transform_node(raw_node)
        if not source_concept:
            return None

        return SourceName(
            **source_concept.model_dump(),
            date_of_birth=raw_concept.date_of_birth,
            date_of_death=raw_concept.date_of_death,
            place_of_birth=raw_concept.place_of_birth,
        )

    def extract_edges(
        self, raw_edge: tuple[dict, WikidataEdgeQueryType]
    ) -> Generator[BaseEdge]:
        edge, edge_type = raw_edge
        if edge_type in PEOPLE_RELATIONSHIP_EDGE_TYPES:
            attributes = SourceNameRelatedToAttributes(relationship_type=edge_type)
            yield SourceNameRelatedTo(
                from_id=edge["from_id"],
                to_id=edge["to_id"],
                attributes=attributes,
            )
        else:
            yield from super().extract_edges(raw_edge)
