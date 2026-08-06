"""
MARCXML reading primitives + the CanonicalRecord intermediate model.

This module knows *how* to pull values out of MARCXML (namespace handling,
control vs. data fields) but deliberately not *which* MARC field feeds which
record field — that single source of truth lives in ``mapping.MARC_SOURCE``.
``builders._extract_record()`` drives the ``extract()`` primitive below from that
table to populate a :class:`CanonicalRecord`.
"""

from __future__ import annotations

from dataclasses import dataclass

from pymarc.record import Record

from adapters.transformers.marc.common import first_non_empty_subfield
from utils.marc import parse_single_marc_record


class MappingError(ValueError):
    """Raised when a required MARC field is absent or a lookup cannot be resolved."""


@dataclass
class CanonicalRecord:
    """Intermediate model extracted from a single MARCXML record."""

    source_id: str
    title: str | None = None  # 245$a
    object_number: str | None = None  # 035$a — AxC local identifier
    object_category: str | None = None  # 655$a — feeds material type
    current_location: str | None = None  # 852$b — AxC current location
    barcode: str | None = None  # 949$a
    loan_type_code: str | None = None  # 949$l
    deleted: bool = False


# ── extraction primitives ─────────────────────────────────────────────────────


def parse_xml(xml_content: str) -> Record:
    """Parse a MARCXML string into a pymarc Record."""
    return parse_single_marc_record(xml_content)


def extract(record: Record, spec: str) -> str | None:
    """Extract one value using ``"TAG$subfield"`` (datafield) or ``"TAG"`` (controlfield).

    This is the only field-access primitive callers need; the table of which
    spec feeds which record field lives in ``mapping.MARC_SOURCE``.
    """
    if "$" in spec:
        tag, code = spec.split("$", 1)
        return first_non_empty_subfield(tag.strip(), code.strip(), record)
    # Control field (e.g. 001, 003, 005, 008)
    tag = spec.strip()
    fields = record.get_fields(tag)
    if not fields:
        return None
    value: str = getattr(fields[0], "data", "") or ""
    return value.strip() or None
