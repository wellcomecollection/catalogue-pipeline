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
    MintRequest,
    SourceIdentifierKey,
)
from models.pipeline.identifier import SourceIdentifier

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


def extract_source_identifiers(
    work_json: dict,
) -> list[MintRequest]:
    return [
        (
            make_key(node["sourceIdentifier"]),
            make_key(node["predecessorIdentifier"])
            if "predecessorIdentifier" in node
            else None,
        )
        for node in scan(work_json, lambda node: "sourceIdentifier" in node)
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
) -> dict:
    source_id = SourceIdentifier.model_validate(work_json["state"]["sourceIdentifier"])

    mint_requests = extract_source_identifiers(work_json)
    predecessor_count = sum(1 for _, pred in mint_requests if pred is not None)

    logger.info(
        "Embedding canonical IDs",
        source_identifier=str(source_id),
        source_identifier_count=len(mint_requests),
        predecessor_count=predecessor_count,
    )

    id_map = resolver.mint_ids(mint_requests)

    result = embed_canonical_ids(work_json, id_map)

    logger.info(
        "Finished embedding canonical IDs",
        source_identifier=str(source_id),
        ids_embedded=len(id_map),
    )

    return result
