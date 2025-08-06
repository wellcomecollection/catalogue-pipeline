from collections.abc import Generator

from pydantic import BaseModel

from ingestor.models.denormalised.work import (
    AllIdentifiers,
    SourceIdentifier,
    Unidentifiable,
)

from .id_label import DisplayIdLabel

IDENTIFIER_LABEL_MAPPING = {
    "tei-manuscript-id": "Tei manuscript id",
    "miro-image-number": "Miro image number",
    "miro-library-reference": "Miro library reference",
    "sierra-system-number": "Sierra system number",
    "sierra-identifier": "Sierra identifier",
    "ebsco-alt-lookup": "EBSCO lookup identifier",
    "lc-gmgpc": "Library of Congress Thesaurus for Graphic Materials",
    "lc-subjects": "Library of Congress Subject Headings (LCSH)",
    "lc-names": "Library of Congress Name authority records",
    "nlm-mesh": "Medical Subject Headings (MeSH) identifier",
    "calm-ref-no": "Calm RefNo",
    "calm-altref-no": "Calm AltRefNo",
    "calm-record-id": "Calm RecordIdentifier",
    "isbn": "International Standard Book Number",
    "issn": "ISSN",
    "mets": "METS",
    "mets-image": "METS image",
    "wellcome-digcode": "Wellcome digcode",
    "iconographic-number": "Iconographic number",
    "viaf": "VIAF: The Virtual International Authority File",
    "fihrist": "Fihrist Authority",
    "bl-estc-citation-no": "British Library English Short Title Catalogue",
    "label-derived": "Identifier derived from the label of the referent",
    "wellcome-accession-number": "Accession number",
    "wikidata": "Wikidata",
}


class DisplayIdentifierType(DisplayIdLabel):
    type: str = "IdentifierType"


class DisplayIdentifier(BaseModel):
    value: str
    type: str = "Identifier"
    identifierType: DisplayIdentifierType

    @staticmethod
    def from_source_identifier(identifier: SourceIdentifier) -> "DisplayIdentifier":
        type_label = IDENTIFIER_LABEL_MAPPING[identifier.identifierType.id]
        return DisplayIdentifier(
            value=identifier.value,
            identifierType=DisplayIdentifierType(
                id=identifier.identifierType.id, label=type_label
            ),
        )

    @staticmethod
    def from_all_identifiers(
        identifier: AllIdentifiers | Unidentifiable,
    ) -> Generator["DisplayIdentifier"]:
        if isinstance(identifier, Unidentifiable):
            return None

        if identifier.sourceIdentifier is not None:
            yield DisplayIdentifier.from_source_identifier(identifier.sourceIdentifier)

        for other_identifier in identifier.otherIdentifiers:
            yield DisplayIdentifier.from_source_identifier(other_identifier)
