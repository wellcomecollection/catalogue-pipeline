from pymarc.record import Record

from adapters.transformers.marc.common import non_empty_subfields
from adapters.transformers.utils.html import normalise_text


def extract_description(record: Record) -> str | None:
    description = " ".join(non_empty_subfields("520", "a", record))
    return normalise_text(description) or None
