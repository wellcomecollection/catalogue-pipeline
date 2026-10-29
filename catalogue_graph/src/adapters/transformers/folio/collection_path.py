from pymarc.record import Record

from adapters.transformers.marc.common import first_non_empty_subfield
from adapters.transformers.marc.predecessor_identifier import (
    extract_sierra_predecessor_id,
)


def extract_collection_identifier(record: Record) -> str | None:
    """The identifier other records use to link to this one in 773/774 ǂw.

    Cataloguers link by the unique identifier in 099 ǂa, or by the Sierra
    b-number (907 ǂa in FOLIO) when the record has no unique identifier.

    A record can only have one identifier here, so every part must link to its
    host by the same one. A part that links to a host with a unique identifier
    by its b-number alone stays detached. Agreed with Collections Information,
    see wellcomecollection/platform#6562.
    """
    return first_non_empty_subfield(
        "099", "a", record
    ) or extract_sierra_predecessor_id(record)
