"""
MARCXML reading primitives + the CanonicalRecord intermediate model.

This module knows *how* to pull values out of MARCXML (namespace handling,
control vs. data fields) but deliberately not *which* MARC field feeds which
record field — that single source of truth lives in ``mapping.MARC_SOURCE``.
``mapping.parse_marcxml()`` drives the ``extract()`` primitive below from that
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
    instance_hrid: str
    holdings_hrid: str
    title: str | None = None
    location_code: str | None = None
    call_number: str | None = None
    call_number_prefix: str | None = None
    shelving_order: str | None = None
    barcode: str | None = None
    material_type_code: str | None = None
    loan_type_code: str | None = None
    copy_number: str | None = None
    volume: str | None = None
    electronic_access_uri: str | None = None
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
