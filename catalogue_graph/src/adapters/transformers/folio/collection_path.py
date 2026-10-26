from pymarc.record import Record

from adapters.transformers.marc.common import first_non_empty_subfield
from adapters.transformers.marc.predecessor_identifier import (
    extract_sierra_predecessor_id,
)


def extract_collection_identifier(record: Record) -> str | None:
    """The identifier other records use to link to this one in 773/774 ǂw.

    Cataloguers link by the unique identifier in 099 ǂa, or by the Sierra
    b-number (907 ǂa in FOLIO) when the record has no unique identifier.
    """
    return first_non_empty_subfield(
        "099", "a", record
    ) or extract_sierra_predecessor_id(record)
