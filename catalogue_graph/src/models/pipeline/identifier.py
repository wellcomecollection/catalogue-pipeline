import unicodedata
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

    @staticmethod
    def identifier_from_text(label: str, ontology_type: str) -> "Identifiable":
        """Create an Identifiable from a free-text label mirroring Scala identifierFromText.

        Steps (kept intentionally close to Scala implementation in
        LabelDerivedIdentifiers.identifierFromText):
        1. Remove a single trailing period if present.
        2. Trim surrounding whitespace.
        3. Lowercase the string.
        4. Unicode normalize (NFKD) then drop all non-ASCII code points.
        5. Trim again (in case normalization introduced spacing changes).
        6. Truncate to 255 characters (DB column width parity).
        7. Return an Identifiable with identifier_type 'label-derived'.
        """

        from adapters.ebsco.transformers.text_utils import trim_trailing_period

        # Step 1 & 2: remove a single trailing period (not ellipsis) then strip surrounding whitespace
        working = trim_trailing_period(label).strip()
        # Step 3
        working = working.lower()
        # Step 4: normalize & remove non-ascii
        normalized = unicodedata.normalize("NFKD", working)
        ascii_only = "".join(ch for ch in normalized if ord(ch) < 128)
        # Step 5
        ascii_only = ascii_only.strip()
        # Step 6
        truncated = ascii_only[:255].strip()
        # No further trailing-period logic; handled by shared trim_trailing_period

        source_identifier = SourceIdentifier(
            identifier_type=Id(id="label-derived"),
            ontology_type=ontology_type,
            value=truncated,
        )
        return Identifiable.from_source_identifier(source_identifier)


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
