from functools import lru_cache

from utils.aws import fetch_transformer_output_from_s3
from utils.types import NodeType, OntologyType


@lru_cache
def _get_ids_for_ontology_and_node_type(
    ontology_type: OntologyType, node_type: NodeType
) -> set[str]:
    """Return all ids classified under a given `node_type` for the selected ontology."""
    if node_type == "names" and ontology_type in ("mesh", "wikidata_linked_mesh"):
        return set()

    print(
        f"Retrieving ids of type '{node_type}' from ontology '{ontology_type}' from S3.",
        end=" ",
        flush=True,
    )
    ids = {
        row[":ID"] for row in fetch_transformer_output_from_s3(node_type, ontology_type)
    }
    print(f"({len(ids)} ids retrieved.)")

    return ids


def is_id_classified_as_node_type(
    item_id: str, item_ontology: OntologyType, node_type: NodeType
) -> bool:
    """
    Return `True` if a given ontology id is classified under the selected node type (concepts, locations, or names).
    """
    return item_id in _get_ids_for_ontology_and_node_type(item_ontology, node_type)


def is_id_in_ontology(item_id: str, item_ontology: OntologyType) -> bool:
    """
    Returns 'True' if the given id from the selected ontology is valid and if it has a corresponding
    SourceConcept/SourceLocation/SourceName node in the catalogue graph.
    """
    if item_ontology == "wikidata":
        return is_id_in_ontology(item_id, "wikidata_linked_loc") or is_id_in_ontology(
            item_id, "wikidata_linked_mesh"
        )
    
    return (
        is_id_classified_as_node_type(item_id, item_ontology, "concepts")
        or is_id_classified_as_node_type(item_id, item_ontology, "locations")
        or is_id_classified_as_node_type(item_id, item_ontology, "names")
    )
