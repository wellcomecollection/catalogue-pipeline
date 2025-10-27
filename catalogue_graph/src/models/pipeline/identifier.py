from collections.abc import Generator
from typing import Literal

from models.pipeline.id_label import Id
from models.pipeline.serialisable import SerialisableModel


class SourceIdentifier(SerialisableModel):
    identifier_type: Id
    ontology_type: str
    value: str

    def __str__(self) -> str:
        return f"Work[{self.identifier_type.id}/{self.value}]"


IdentifyType = Literal["Identifiable", "Identified", "Unidentifiable"]


class Identifiers(SerialisableModel):
    source_identifier: SourceIdentifier
    other_identifiers: list[SourceIdentifier] = []

    def get_identifiers(self) -> Generator[SourceIdentifier]:
        yield self.source_identifier
        yield from self.other_identifiers

    def get_identifier_values(self) -> Generator[str]:
        for identifier in self.get_identifiers():
            yield identifier.value


class Identifiable(Identifiers):
    canonical_id: str | None = None
    type: IdentifyType = "Identifiable"
    identifiedType: IdentifyType = "Identified"

    @staticmethod
    def from_source_identifier(identifier: SourceIdentifier) -> "Identifiable":
        return Identifiable(source_identifier=identifier, other_identifiers=[])


class Identified(Identifiers):
    canonical_id: str
    type: IdentifyType = "Identified"


class Unidentifiable(SerialisableModel):
    canonical_id: None = None
    type: IdentifyType = "Unidentifiable"

    def get_identifiers(self) -> Generator[SourceIdentifier]:
        yield from []

    def get_identifier_values(self) -> Generator[str]:
        yield from []
