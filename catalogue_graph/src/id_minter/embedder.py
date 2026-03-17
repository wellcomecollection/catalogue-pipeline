"""Embedder: resolve and embed canonical IDs into work JSON.

Combines recursive JSON traversal with an IdResolver (e.g. MintingResolver) to
look up or mint canonical IDs for every sourceIdentifier node in a work.
"""

from __future__ import annotations

from collections.abc import Callable, Iterator
from typing import Any, cast

import structlog

from id_minter.models.identifier import (
    TYPES_NORMALIZED_TO_CONCEPT,
    IdResolver,
    SourceId,
    SourceIdentifierKey,
)

logger = structlog.get_logger(__name__)


def scan(obj: Any, predicate: Callable[[dict], bool]) -> Iterator[dict]:
    if isinstance(obj, dict):
        if predicate(obj):
            yield obj
        for v in obj.values():
            yield from scan(v, predicate)
    elif isinstance(obj, list):
        for item in obj:
            yield from scan(item, predicate)


def transform(
    obj: Any, predicate: Callable[[dict], bool], fn: Callable[[dict], dict]
) -> Any:
    if isinstance(obj, dict):
        transformed = {k: transform(v, predicate, fn) for k, v in obj.items()}
        return fn(transformed) if predicate(transformed) else transformed
    elif isinstance(obj, list):
        return [transform(item, predicate, fn) for item in obj]
    return obj


def normalize_ontology_type(ontology_type: str) -> str:
    """Normalize concept subtypes to 'Concept' for ID lookup.

    Matches Scala's ConceptsSourceIdentifierAdjuster: when minting IDs for
    concepts, we don't care about ontology types. For example, an 'Agent' with
    a given Library of Congress source identifier should have the same ID as a
    'Person' with the same source identifier.
    """
    if ontology_type in TYPES_NORMALIZED_TO_CONCEPT:
        return "Concept"
    return ontology_type


def make_key(source_identifier: dict) -> SourceIdentifierKey:
    return SourceIdentifierKey(
        ontology_type=normalize_ontology_type(source_identifier["ontologyType"]),
        source_system=source_identifier["identifierType"]["id"],
        source_id=source_identifier["value"],
    )


def extract_source_identifiers(work_json: dict) -> list[SourceIdentifierKey]:
    return [
        make_key(node["sourceIdentifier"])
        for node in scan(work_json, lambda d: "sourceIdentifier" in d)
    ]


def embed_canonical_ids(
    work_json: dict, id_map: dict[SourceIdentifierKey, str]
) -> dict:
    """Add canonical IDs and promote minted nodes to the identified shape.

    ``id_map`` maps SourceIdentifierKey -> canonical ID string.
    Nodes whose sourceIdentifier is not found in ``id_map`` are left unchanged.
    """

    def _add_canonical_id(node: dict) -> dict:
        key = make_key(node["sourceIdentifier"])
        canonical_id = id_map.get(key)
        if canonical_id is not None:
            updated_node = {**node, "canonicalId": canonical_id}

            identified_type = updated_node.get("identifiedType")
            if isinstance(identified_type, str):
                promoted_node = {
                    k: v for k, v in updated_node.items() if k != "identifiedType"
                }
                promoted_node["type"] = identified_type
                return promoted_node

            if updated_node.get("type") == "Identifiable":
                return {**updated_node, "type": "Identified"}

            return updated_node
        return node

    return cast(
        dict,
        transform(work_json, lambda d: "sourceIdentifier" in d, _add_canonical_id),
    )


def process_work(
    work_json: dict,
    resolver: IdResolver,
    predecessors: dict[SourceIdentifierKey, SourceIdentifierKey] | None = None,
) -> dict:
    # TODO: We have not yet decided how works will indicate their predecessors.
    # The `predecessors` parameter is plumbed through but the mechanism for
    # deriving it from the work JSON needs to be designed and implemented.
    source_id = work_json.get("state", {}).get("sourceIdentifier", {})
    source_id_str = (
        f"{source_id.get('ontologyType', '?')}"
        f"[{source_id.get('identifierType', {}).get('id', '?')}"
        f"/{source_id.get('value', '?')}]"
    )

    preds = predecessors or {}
    keys = extract_source_identifiers(work_json)
    predecessor_count = sum(1 for k in keys if k in preds)
    logger.info(
        "Embedding canonical IDs",
        source_identifier=source_id_str,
        source_identifier_count=len(keys),
        predecessor_count=predecessor_count,
    )

    if not keys:
        logger.info(
            "No source identifiers found, skipping",
            source_identifier=source_id_str,
        )
        return work_json

    requests: list[tuple[SourceId, SourceId | None]] = [
        (
            (k[0], k[1], k[2]),
            (preds[k][0], preds[k][1], preds[k][2]) if k in preds else None,
        )
        for k in keys
    ]
    found = resolver.mint_ids(requests)
    id_map: dict[SourceIdentifierKey, str] = {
        SourceIdentifierKey(*k): v for k, v in found.items()
    }

    result = embed_canonical_ids(work_json, id_map)

    logger.info(
        "Finished embedding canonical IDs",
        source_identifier=source_id_str,
        ids_embedded=len(id_map),
    )

    return result
