"""Title extraction mirroring Scala MarcTitle behaviour.

Rules replicated from Scala implementation (MarcTitle.scala):
  - Use MARC field 245; if multiple, log a warning and choose the first.
  - Select subfields a, b, c, h, n, p in original order.
  - If the last selected subfield is h, drop it entirely.
  - For retained h (i.e. not last), remove ALL bracketed segments ("[..."]) then trim.
  - Other subfields left unchanged (no additional trimming or whitespace collapsing).
  - Join components with a single space.
  - If resulting components list empty, raise ValueError (no custom exception class).
"""

import re

from pymarc.field import Field
from pymarc.record import Record

from adapters.ebsco.transformers.common import mandatory_field

_SUBFIELD_TAGS = {"a", "b", "c", "h", "n", "p"}
_BRACKETED_SEGMENT = re.compile(r"\[[^\]]+\]")


def _get_245_field(marc_record: Record) -> Field:
    fields_245 = marc_record.get_fields("245")
    if not fields_245:
        # Let mandatory_field decorator handle missing field error messaging.
        raise ValueError("Missing title field (245)")
    if len(fields_245) > 1:
        print(
            "Multiple instances of non-repeatable varfield with tag 245: %d (using first)",
            len(fields_245),
        )
    return fields_245[0]


def _selected_subfield_values(field_245: Field) -> list[str]:
    subfields = field_245.subfields
    selected: list[tuple[str, str]] = []
    for sfield in subfields:
        code = sfield.code
        value = sfield.value
        if code in _SUBFIELD_TAGS:
            selected.append((code, value))

    # Drop trailing h if present (single last occurrence only, as per Scala logic)
    if selected and selected[-1][0] == "h":
        selected = selected[:-1]

    components: list[str] = []
    for code, value in selected:
        if code == "h":
            # Remove all bracketed segments then trim
            processed = _BRACKETED_SEGMENT.sub("", value).strip()
            components.append(processed)
        else:
            # Preserve value exactly (including leading/trailing whitespace)
            components.append(value)

    return components


@mandatory_field("245", "title")
def extract_title(marc_record: Record) -> str:
    field_245 = _get_245_field(marc_record)
    components = _selected_subfield_values(field_245)

    if not any(c.strip() for c in components):
        # Provide ValueError matching existing tests pattern.
        raise ValueError("Empty title field (245) after processing subfields")
    return " ".join(components)
