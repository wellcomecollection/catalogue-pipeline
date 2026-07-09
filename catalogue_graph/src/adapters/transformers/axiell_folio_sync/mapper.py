"""
MARCXML reading primitives + the CanonicalRecord intermediate model.

This module knows *how* to pull values out of MARCXML (namespace handling,
control vs. data fields) but deliberately not *which* MARC field feeds which
record field — that single source of truth lives in ``mapping.MARC_SOURCE``.
``mapping.parse_marcxml()`` drives the ``extract()`` primitive below from that
table to populate a :class:`CanonicalRecord`.
"""

from __future__ import annotations

import xml.etree.ElementTree as ET
from dataclasses import dataclass

MARC_NS = {"marc": "http://www.loc.gov/MARC21/slim"}


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


# ── internal XML helpers ──────────────────────────────────────────────────────


def _uses_marc_ns(root: ET.Element) -> bool:
    return root.tag.startswith("{") and "MARC21/slim" in root.tag


def _controlfield(root: ET.Element, tag: str) -> str | None:
    if _uses_marc_ns(root):
        node = root.find(f".//marc:controlfield[@tag='{tag}']", MARC_NS)
    else:
        node = root.find(f".//controlfield[@tag='{tag}']")
    if node is None or not node.text:
        return None
    return node.text.strip() or None


def _first_subfield(root: ET.Element, tag: str, code: str) -> str | None:
    if _uses_marc_ns(root):
        nodes = root.findall(
            f".//marc:datafield[@tag='{tag}']/marc:subfield[@code='{code}']", MARC_NS
        )
    else:
        nodes = root.findall(f".//datafield[@tag='{tag}']/subfield[@code='{code}']")
    for node in nodes:
        value = (node.text or "").strip()
        if value:
            return value
    return None


# ── extraction primitives ─────────────────────────────────────────────────────


def parse_xml(xml_content: str) -> ET.Element:
    """Parse a MARCXML string into an ElementTree root."""
    return ET.fromstring(xml_content)


def extract(root: ET.Element, spec: str) -> str | None:
    """Extract one value using ``"TAG$subfield"`` (datafield) or ``"TAG"`` (controlfield).

    This is the only field-access primitive callers need; the table of which
    spec feeds which record field lives in ``mapping.MARC_SOURCE``.
    """
    if "$" in spec:
        tag, code = spec.split("$", 1)
        return _first_subfield(root, tag.strip(), code.strip())
    return _controlfield(root, spec.strip())
