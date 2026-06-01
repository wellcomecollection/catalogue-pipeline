from abc import ABC, abstractmethod
from collections.abc import Generator
from functools import lru_cache
from typing import Any, cast

import structlog

from core.source import BaseSource
from models.events import ExtractorEvent
from utils.ontology import (
    is_id_extracted_for_ontology,
    is_id_extracted_for_transformer,
)
from utils.types import NodeType, OntologyType, TransformerType

from .sparql_client import WikidataSparqlClient
from .sparql_query_builder import SparqlQueryBuilder, WikidataEdgeQueryType

logger = structlog.get_logger(__name__)

HAS_PARENT_EDGE_TYPES: list[WikidataEdgeQueryType] = ["instance_of", "subclass_of"]
PEOPLE_RELATIONSHIP_EDGE_TYPES: list[WikidataEdgeQueryType] = [
    "has_father",
    "has_mother",
    "has_sibling",
    "has_spouse",
    "has_child",
]

WIKIDATA_ID_PREFIX = "http://www.wikidata.org/entity/"


def extract_wikidata_id(item: dict, key: str = "item") -> str | None:
    """
    Accepts a raw `item` dictionary returned by the Wikidata SPARQL endpoint and returns the Wikidata id of the item.
    Returns `None` if the stored id is not valid.
    """
    wikidata_id = item[key]["value"]
    assert isinstance(wikidata_id, str)
    assert item[key]["type"] == "uri"

    if wikidata_id.startswith(WIKIDATA_ID_PREFIX):
        return wikidata_id.removeprefix(WIKIDATA_ID_PREFIX)

    # Very rarely, Wikidata returns an invalid ID in the format
    # http://www.wikidata.org/.well-known/genid/<some hexadecimal string>.
    # Log when this happens and return 'None'.
    logger.warning("Encountered an invalid Wikidata id", wikidata_id=wikidata_id)
    return None


class WikidataLinkedOntologySource(BaseSource, ABC):
    """
    A source for streaming selected Wikidata nodes/edges. There are _many_ Wikidata items, so we cannot store all of
    them in the graph. Instead, we only include items which reference an ID from a selected linked ontology
    (LoC or MeSH) and their parents.

    Wikidata puts strict limits on the resources which can be consumed by a single query, and queries which include
    filters or do other expensive processing often time out or return a stack overflow error. This means we need
    to use a somewhat convoluted way for extracting the Wikidata nodes/edges we need.
    See https://www.wikidata.org/wiki/Wikidata:SPARQL_query_service/query_optimization for more information on how
    to optimise SPARQL queries.
    """

    def __init__(self, linked_transformer: TransformerType, event: ExtractorEvent):
        self.client = WikidataSparqlClient()
        self.linked_transformer = linked_transformer
        self.event = event

        # Transformer type strings are always in the format `<ontology_type>_<node_type>`
        self.linked_ontology = cast(OntologyType, self.linked_transformer.split("_")[0])
        self.node_type = cast(NodeType, self.linked_transformer.split("_")[-1])

        self.same_as_edge_type = cast(
            WikidataEdgeQueryType, f"same_as_{self.linked_ontology}"
        )

    @lru_cache
    def _get_all_ids(self) -> list[str]:
        """
        Return the IDs of all Wikidata items which reference a node from the selected linked ontology.
        All IDs are returned, regardless of whether we categorise them as concepts, names, or locations.
        """
        logger.info(
            "Retrieving Wikidata IDs linked to ontology",
            linked_ontology=self.linked_ontology,
        )
        ids_query = SparqlQueryBuilder.get_all_ids_query(self.linked_ontology)
        id_items = self.client.run_query(ids_query)

        # Deduplicate. (We could deduplicate as part of the SPARQL query via the 'DISTINCT' keyword,
        # but that would make the query significantly slower. It's faster to deduplicate here.)
        all_ids = set(extract_wikidata_id(item) for item in id_items)
        all_valid_ids = [i for i in all_ids if i is not None]

        logger.info("Retrieved Wikidata IDs", count=len(all_valid_ids))
        return list(all_valid_ids)

    def _stream_all_edges_by_type(
        self, edge_type: WikidataEdgeQueryType
    ) -> Generator[dict]:
        """
        Return a generator of all edges of the given `edge_type` for Wikidata items referencing the selected ontology.

        Edges are extracted via the following steps:
            1. Run a SPARQL query which retrieves _all_ Wikidata items referencing an ID from the linked ontology.
            2. Split the returned IDs into chunks and run a second SPARQL query per chunk to retrieve the requested
            edges. (It is possible to modify the query in step 1 to return the edges directly, but this makes the
            query unreliable - sometimes it times out or returns invalid JSON. Getting the edges in chunks is much
            slower, but it works every time.)
        """
        logger.info("Streaming edges from Wikidata", edge_type=edge_type)

        def build_edge_query(wikidata_ids: list[str]) -> str:
            return SparqlQueryBuilder.get_edge_query(wikidata_ids, edge_type)

        all_ids = self._get_all_ids()
        for raw_mapping in self.client.run_query_in_parallel(
            iter(all_ids), build_edge_query
        ):
            from_id = extract_wikidata_id(raw_mapping, "fromItem")

            # The 'toItem' IDs of SAME_AS edges are MeSH/LoC IDs, so we take the raw value instead of extracting
            # the IDs via the `extract_wikidata_id` function
            if edge_type in ("same_as_mesh", "same_as_loc"):
                to_id = raw_mapping["toItem"]["value"]
            else:
                to_id = extract_wikidata_id(raw_mapping, "toItem")

            if from_id is not None and to_id is not None:
                yield {"from_id": from_id, "to_id": to_id}

    def _is_id_valid_for_ontology(self, item_id: str, ontology: OntologyType) -> bool:
        """Return `True` if the given ID is valid for the specified ontology."""
        return is_id_extracted_for_ontology(
            item_id, ontology, self.event.pipeline_date, self.event.environment
        )

    def _is_id_valid_for_transformer(
        self, item_id: str, transformer_type: TransformerType
    ) -> bool:
        """Return `True` if the given ID was extracted by the specified transformer."""
        return is_id_extracted_for_transformer(
            item_id, transformer_type, self.event.pipeline_date, self.event.environment
        )

    @abstractmethod
    def stream_raw(self) -> Generator[Any]: ...
