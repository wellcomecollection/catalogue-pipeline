from models.indexable import DisplayIdentifier, DisplayIdentifierType

IDENTIFIER_LABEL_MAPPING = {
    "lc-subjects": "Library of Congress Subject Headings (LCSH)",
    "lc-names": "Library of Congress Name authority records",
    "nlm-mesh": "Medical Subject Headings (MeSH) identifier",
    "viaf": "VIAF: The Virtual International Authority File",
    "fihrist": "Fihrist Authority",
    "label-derived": "Identifier derived from the label of the referent",
    "wikidata": "Wikidata",
}


def get_display_identifier(value: str, identifier_type: str) -> DisplayIdentifier:
    type_label = IDENTIFIER_LABEL_MAPPING[identifier_type]
    return DisplayIdentifier(
        value=value,
        identifierType=DisplayIdentifierType(id=identifier_type, label=type_label),
    )
