from collections.abc import Generator
from typing import Literal

from models.pipeline.id_label import Id
from models.pipeline.serialisable import ElasticsearchModel


class SourceIdentifier(ElasticsearchModel):
    identifier_type: Id
    ontology_type: str
    value: str


class Identifiers(ElasticsearchModel):
    canonical_id: str
    source_identifier: SourceIdentifier
    other_identifiers: list[SourceIdentifier] = []

    def get_identifiers(self) -> Generator[SourceIdentifier]:
        yield self.source_identifier
        yield from self.other_identifiers

    def get_identifier_values(self) -> Generator[str]:
        for identifier in self.get_identifiers():
            yield identifier.value


class Unidentifiable(ElasticsearchModel):
    canonical_id: None = None
    type: Literal["Unidentifiable"] = "Unidentifiable"

    def get_identifiers(self) -> Generator[SourceIdentifier]:
        yield from []

    def get_identifier_values(self) -> Generator[str]:
        yield from []
