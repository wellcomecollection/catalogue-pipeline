"""Format from the material type code in MARC 998 ǂd, the interim source until
Collections Information's maintained field exists (platform#6570)."""

from collections.abc import Callable

import structlog
from pymarc.record import Record

from models.pipeline.format import FORMAT_LABEL_MAPPING, Format

logger = structlog.get_logger(__name__)

# The Sierra transformer gives e-resource material types the format they link to,
# so a catalogued e-book is a Book, not an E-book.
LINKED_FORMATS = {"v": "a", "j": "d", "s": "i", "f": "g", "x": "b"}


def material_type_from_998(record: Record) -> str | None:
    """The MAT TYPE code in the first 998 ǂd, trimmed."""
    fields = record.get_fields("998")
    if not fields:
        return None

    codes = fields[0].get_subfields("d")
    if not codes:
        return None

    return codes[0].strip()


# Tried in order, so a maintained source goes first once one exists.
MATERIAL_TYPE_SOURCES: tuple[Callable[[Record], str | None], ...] = (
    material_type_from_998,
)


def resolve_material_type(record: Record) -> str | None:
    """The material type code from the first source that has one."""
    for source in MATERIAL_TYPE_SOURCES:
        if (code := source(record)) is not None:
            return code

    return None


def extract_format(record: Record) -> Format | None:
    code = resolve_material_type(record)
    if code is None:
        return None

    if len(code) != 1:
        logger.warning("Material type code is not a single character", code=code)
        return None

    code = LINKED_FORMATS.get(code, code)
    if (label := FORMAT_LABEL_MAPPING.get(code)) is None:
        logger.warning("Unrecognised material type code", code=code)
        return None

    return Format(id=code, label=label)
