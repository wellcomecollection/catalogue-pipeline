from collections.abc import Generator

from pydantic import BaseModel

from models.identifier_schemes import IDENTIFIER_LABEL_MAPPING
from models.pipeline.identifier import (
    Identifiable,
    Identified,
    SourceIdentifier,
    Unidentifiable,
)

from .id_label import DisplayIdLabel


class DisplayIdentifierType(DisplayIdLabel):
    type: str = "IdentifierType"


class DisplayIdentifier(BaseModel):
    value: str
    type: str = "Identifier"
    identifierType: DisplayIdentifierType

    @staticmethod
    def from_source_identifier(identifier: SourceIdentifier) -> "DisplayIdentifier":
        type_label = IDENTIFIER_LABEL_MAPPING[identifier.identifier_type.id]
        return DisplayIdentifier(
            value=identifier.value,
            identifierType=DisplayIdentifierType(
                id=identifier.identifier_type.id, label=type_label
            ),
        )

    @staticmethod
    def from_all_identifiers(
        identifier: Identified | Unidentifiable | Identifiable,
    ) -> Generator["DisplayIdentifier"]:
        for i in identifier.get_identifiers():
            yield DisplayIdentifier.from_source_identifier(i)
