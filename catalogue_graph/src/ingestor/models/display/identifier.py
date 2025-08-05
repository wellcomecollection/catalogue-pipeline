from ingestor.models.indexable import DisplayIdentifier, DisplayIdentifierType

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


def get_display_identifier(value: str, identifier_type: str) -> DisplayIdentifier:
    type_label = IDENTIFIER_LABEL_MAPPING[identifier_type]
    return DisplayIdentifier(
        value=value,
        identifierType=DisplayIdentifierType(id=identifier_type, label=type_label),
    )
