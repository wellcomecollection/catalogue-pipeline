"""
Extracting other identifiers from
https://www.loc.gov/marc/bibliographic/bd035.html

The origin codes for Axiell records from the Mimsy dataset correspond to the
identifier types that were stored there

"""

import logging
from collections.abc import Iterable
from itertools import chain

from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.transformers.common import is_url
from models.pipeline.identifier import Id, SourceIdentifier

logger = logging.getLogger("transformer/other_identifiers")


def extract_other_identifiers(record: Record) -> list[SourceIdentifier]:
    return [
        source_id
        for source_id in (format_field(field) for field in record.get_fields("035"))
        if source_id is not None
    ]


ORIGIN_CODE_TO_ID_TYPE = {
    "Bibliographic Number": "sierra-system-number",
    "Mimsy reference": "mimsy-reference",
    "Sierra Number": "sierra-identifier",
    "WI number": "miro-image-number",
    "accession number": "wellcome-accession-number",
    # "Library Reference Number" is handled specially in format_field
    # Two other id schemes exist, but I don't know what to do with them.
    # "SCM loan accession number": ,
    # "temporary number": ,
}


def format_field(field: Field) -> str:
    a_subfield = field.get("a")
    assert a_subfield is not None
    prefix, rpar, id_value = a_subfield[1:].partition(")")
    if not rpar:
        logger.error("identifier without namespace prefix: %s", a_subfield)
        return None
    identifier_type = which_identifier_type(prefix, id_value)
    if identifier_type is None:
        if prefix == "SCM loan accession number" or prefix == "temporary number":
            # don't bother warning, we know about these and don't have a use for them yet
            # logging them would just clutter the logs
            return None
        logger.warning(
            "unknown identifier prefix '%s' in identifier: %s", prefix, a_subfield
        )
        return None

    return SourceIdentifier(
        identifier_type=Id(id=identifier_type), ontology_type="Work", value=id_value
    )


def which_identifier_type(prefix: str, id_value: str) -> str | None:
    if prefix == "Library Reference Number":
        if "/" in id_value:
            return "calm-alt-ref-no"
        else:
            return "iconographic-number"
    return ORIGIN_CODE_TO_ID_TYPE.get(prefix)
