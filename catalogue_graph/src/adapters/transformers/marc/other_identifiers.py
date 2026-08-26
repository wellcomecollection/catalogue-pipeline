"""
Extracting other identifiers from
https://www.loc.gov/marc/bibliographic/bd035.html

The origin codes for Axiell records from the Mimsy dataset correspond to the
identifier types that were stored there

"""

import structlog
from pymarc.field import Field
from pymarc.record import Record

from models import identifier_schemes
from models.identifier_schemes import IdentifierScheme
from models.pipeline.identifier import Id, SourceIdentifier

logger = structlog.get_logger(__name__)


def extract_other_identifiers(record: Record) -> list[SourceIdentifier]:
    return [
        source_id
        for source_id in (format_field(field) for field in record.get_fields("035"))
        if source_id is not None
    ]


ORIGIN_CODE_TO_ID_TYPE = {
    "Bibliographic Number": identifier_schemes.SIERRA_SYSTEM_NUMBER,
    "Mimsy reference": identifier_schemes.MIMSY_REFERENCE,
    "Sierra Number": identifier_schemes.SIERRA_IDENTIFIER,
    "WI number": identifier_schemes.MIRO_IMAGE_NUMBER,
    "accession number": identifier_schemes.WELLCOME_ACCESSION_NUMBER,
    "Calm RefNo": identifier_schemes.CALM_REF_NO,
    "AltRefNo": identifier_schemes.CALM_ALTREF_NO,
    # "Library Reference Number" is handled specially in format_field
    # Two other id schemes exist, but I don't know what to do with them.
    # "SCM loan accession number": ,
    # "temporary number": ,
}


IGNORED_PREFIXES = {
    "SCM loan accession number",
    "temporary number",
    "Other number",
    "Previous number",
    "Previouse number",
    "Archivematica UUID",
    "AV  barcode",
}


def format_field(field: Field) -> SourceIdentifier | None:
    a_subfield = field.get("a")
    if a_subfield is None:
        logger.error("035 field without subfield 'a': %r", field)
        return None
    prefix, rpar, id_value = a_subfield[1:].partition(")")
    if not rpar:
        logger.error("identifier without namespace prefix: %s", a_subfield)
        return None
    identifier_type = which_identifier_type(prefix, id_value)
    if identifier_type is None:
        # Do not warn about known ignored prefixes. We don't have a use for them
        # and logging them would clutter the logs.
        if prefix not in IGNORED_PREFIXES:
            logger.warning(
                "Unknown identifier prefix", prefix=prefix, identifier_value=a_subfield
            )

        return None

    # Axiell records always have a redundant "Acc" prefix, even when it is not followed by a value.
    # We remove the prefix as a temporary fix.
    # TODO: This issue should be fixed at source.
    if identifier_type == identifier_schemes.WELLCOME_ACCESSION_NUMBER:
        id_value = id_value.removeprefix("Acc").strip()

    # Axiell Collections holds Sierra bib numbers in the ".b1234567x" notation (an
    # artifact of the data migration), but the Sierra adapter and the rest of the
    # pipeline use the canonical "b1234567x". Without stripping the leading dot the
    # matcher never links an Axiell work to its Sierra record, so they fail to merge.
    if identifier_type == identifier_schemes.SIERRA_SYSTEM_NUMBER:
        id_value = id_value.lstrip(".")

    if not id_value:
        return None

    return SourceIdentifier(
        identifier_type=Id(id=identifier_type.id), ontology_type="Work", value=id_value
    )


def which_identifier_type(prefix: str, id_value: str) -> IdentifierScheme | None:
    if prefix == "Library Reference Number":
        if "/" in id_value:
            return identifier_schemes.CALM_ALTREF_NO
        return identifier_schemes.ICONOGRAPHIC_NUMBER
    return ORIGIN_CODE_TO_ID_TYPE.get(prefix)
