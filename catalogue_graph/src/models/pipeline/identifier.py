from collections.abc import Generator
from typing import Literal

from models.pipeline.id_label import Id
from models.pipeline.serialisable import ElasticsearchModel


class SourceIdentifier(ElasticsearchModel):
    identifier_type: Id
    ontology_type: str
    value: str

    def __str__(self) -> str:
        return f"Work[{self.identifier_type.id}/{self.value}]"


IdentifyType = Literal["Identified", "Unidentifiable"]


class BaseIdentify(ElasticsearchModel):
    canonical_id: str | None = None
    identified_type: IdentifyType

    def get_identifiers(self) -> Generator[SourceIdentifier]:
        raise NotImplementedError()

    def get_identifier_values(self) -> Generator[str]:
        raise NotImplementedError()


class Identifiable(BaseIdentify):
    source_identifier: SourceIdentifier
    other_identifiers: list[SourceIdentifier] = []
    identified_type: IdentifyType = "Identified"

    def get_identifiers(self) -> Generator[SourceIdentifier]:
        yield self.source_identifier
        yield from self.other_identifiers

    def get_identifier_values(self) -> Generator[str]:
        for identifier in self.get_identifiers():
            yield identifier.value

    @staticmethod
    def from_source_identifier(identifier: SourceIdentifier) -> "Identifiable":
        return Identifiable(source_identifier=identifier, other_identifiers=[])


class Identified(Identifiable):
    canonical_id: str


class Unidentifiable(BaseIdentify):
    canonical_id: None = None
    identified_type: IdentifyType = "Unidentifiable"

    def get_identifiers(self) -> Generator[SourceIdentifier]:
        yield from []

    def get_identifier_values(self) -> Generator[str]:
        yield from []
