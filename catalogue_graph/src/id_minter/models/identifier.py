from __future__ import annotations

from typing import NamedTuple, Protocol, runtime_checkable

from utils.types import ConceptType

# Ontology types that are normalized to "Concept" before DB lookup.
# Kept in sync with the Scala id_minter's ConceptsSourceIdentifierAdjuster.
TYPES_NORMALIZED_TO_CONCEPT: frozenset[ConceptType] = frozenset(
    {"Person", "Organisation", "Place", "Agent", "Meeting", "Genre", "Period"}
)

SourceId = tuple[str, str, str]


class SourceIdentifierKey(NamedTuple):
    ontology_type: str
    source_system: str
    source_id: str


@runtime_checkable
class IdResolver(Protocol):
    def lookup_ids(self, source_ids: list[SourceId]) -> dict[SourceId, str]: ...

    def mint_ids(
        self, requests: list[tuple[SourceId, SourceId | None]]
    ) -> dict[SourceId, str]: ...
