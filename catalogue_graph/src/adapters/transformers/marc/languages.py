"""
Extracting languages from the Sierra LANG fixed field (MARC 998 ǂf), falling
back to the MARC language code in 008/35-37, plus additional languages from 041.

https://www.loc.gov/marc/bibliographic/bd008a.html
https://www.loc.gov/marc/bibliographic/bd041.html
"""

import structlog
from pymarc.record import Record

from adapters.transformers.ebsco.parsers.field008 import RawField008
from lookups.languages import from_code
from models.pipeline.id_label import Language

logger = structlog.get_logger(__name__)

# Codes that tell us nothing about the language, so are not worth displaying.
SUPPRESSED_CODES = {
    "mul",  # Multiple languages
    "und",  # Undetermined
    "zxx",  # No linguistic content
}


def extract_languages(record: Record) -> list[Language]:
    """The primary language first, then 041 ǂa in document order, deduplicated."""
    codes = [_primary_code(record)] + [
        value
        for field in record.get_fields("041")
        for value in field.get_subfields("a")
    ]

    languages: list[Language] = []
    for code in codes:
        language = _resolve(code)
        if language is not None and language not in languages:
            languages.append(language)

    return languages


def _primary_code(record: Record) -> str | None:
    """Prefer 998 ǂf, the Sierra LANG field, which is curated where 008/35-37 is often
    left as fill characters, `und`, or stale. A blank ǂf is a deliberate "no language",
    so only a missing ǂf falls back to 008.

    TODO: 998 is a Sierra field carried over by the migration. Confirm whether FOLIO
    keeps maintaining ǂf, or whether records edited in FOLIO update only 008/35-37,
    in which case the preference here should flip after cutover.
    """
    for field in record.get_fields("998"):
        if values := field.get_subfields("f"):
            return values[0]

    if field_008 := RawField008.from_record(record):
        return field_008.languagecode

    return None


def _resolve(code: str | None) -> Language | None:
    """Codes that are absent, mean "no language", or are suppressed produce nothing."""
    if code is None or _is_no_language(code):
        return None

    language = from_code(code.strip().lower())
    if language is None:
        # TODO: Some 041 ǂa values pack several codes into one subfield (e.g. "engger").
        # Matching the Scala, these are dropped; they could be split into 3-character codes.
        logger.error("Unrecognised language code", code=code)
        return None

    if language.id in SUPPRESSED_CODES:
        return None

    return language


def _is_no_language(code: str) -> bool:
    """Blanks and MARC fill characters both mean the record has no language, which is
    not a data error: some records legitimately have none, e.g. paintings."""
    return not code.strip("| ")
