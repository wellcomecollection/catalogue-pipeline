from __future__ import annotations

from typing import NamedTuple, Protocol, runtime_checkable

from utils.types import ConceptType

# Ontology types that are normalized to "Concept" before DB lookup.
# Kept in sync with the Scala id_minter's ConceptsSourceIdentifierAdjuster.
TYPES_NORMALIZED_TO_CONCEPT: frozenset[ConceptType] = frozenset(
    {"Person", "Organisation", "Place", "Agent", "Meeting", "Genre", "Period"}
)


class SourceIdentifierKey(NamedTuple):
    ontology_type: str
    source_system: str
    source_id: str


# A single mint request: (source_identifier, predecessor_or_none).
# A None predecessor means "mint a fresh canonical ID"; a non-None predecessor
# means "inherit the predecessor's canonical ID" (used during source system
# migrations to preserve stable identifiers).
MintRequest = tuple[SourceIdentifierKey, SourceIdentifierKey | None]


@runtime_checkable
class IdResolver(Protocol):
    def lookup_ids(
        self, source_ids: list[SourceIdentifierKey]
    ) -> dict[SourceIdentifierKey, str]: ...

    def mint_ids(
        self, requests: list[MintRequest]
    ) -> dict[SourceIdentifierKey, str]: ...
