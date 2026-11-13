from pymarc.record import Record

from adapters.transformers.marc.common import non_empty_subfields
from adapters.transformers.utils.html import sanitise_html


def extract_physical_description(record: Record) -> str | None:
    description = " ".join(non_empty_subfields("300", "a", record))
    return sanitise_html(description) or None
