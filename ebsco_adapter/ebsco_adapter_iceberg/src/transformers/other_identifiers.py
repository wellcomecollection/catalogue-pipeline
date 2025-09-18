"""
Other Identifiers include ISBN (020) and ISSN (022) records.
* https://www.loc.gov/marc/bibliographic/bd020.html
* https://www.loc.gov/marc/bibliographic/bd022.html
"""

from pymarc.field import Field
from pymarc.record import Record

from models.work import SourceIdentifier

ID_TYPES = {"020": "isbn", "022": "issn"}


def extract_other_identifiers(record: Record) -> list[SourceIdentifier]:
    id_fields = record.get_fields("020", "022")
    return [to_standard_identifier(field) for field in id_fields if "a" in field]


def to_standard_identifier(field: Field) -> SourceIdentifier:
    return SourceIdentifier(
        identifier_type=ID_TYPES[field.tag], ontology_type="Work", value=field["a"]
    )
