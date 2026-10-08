"""
Extracting languages from the MARC language code in 008/35-37, plus additional
languages from 041. EBSCO works carry only the primary language; FOLIO works
carry both.

The Sierra LANG fixed field, which the Scala transformer reads, is carried into
Folio as 998 ǂf. Folio's MARC-to-Instance mapping does not read ǂf at all, so
nothing maintains it; 008/35-37 and 041 ǂa are the fields it maps to languages.

https://www.loc.gov/marc/bibliographic/bd008a.html
https://www.loc.gov/marc/bibliographic/bd041.html
"""

import structlog
from pymarc.record import Record

from adapters.transformers.marc.parsers.field008 import RawField008
from lookups.languages import from_code
from models.pipeline.id_label import Language

logger = structlog.get_logger(__name__)

# Codes that tell us nothing about the language, so are not worth displaying.
SUPPRESSED_CODES = {
    "mul",  # Multiple languages
    "und",  # Undetermined
    "zxx",  # No linguistic content
}


def extract_primary_language(record: Record) -> Language | None:
    """The language coded in 008/35-37."""
    return _resolve(_primary_code(record))


def extract_languages(record: Record) -> list[Language]:
    """The primary language first, then 041 ǂa in document order, deduplicated."""
    languages: list[Language] = []
    primary = extract_primary_language(record)
    if primary is not None:
        languages.append(primary)

    for field in record.get_fields("041"):
        for value in field.get_subfields("a"):
            for code in _split_packed_codes(value):
                language = _resolve(code)
                if language is not None and language not in languages:
                    languages.append(language)

    return languages


def _primary_code(record: Record) -> str | None:
    """The primary language is the MARC language code in 008/35-37."""
    if field_008 := RawField008.from_record(record):
        return field_008.languagecode

    return None


def _split_packed_codes(value: str) -> list[str]:
    """Some 041 ǂa subfields pack several codes together (e.g. "engger").

    Folio's own MARC-to-Instance mapping splits ǂa every three characters. We only do
    so when every chunk is a real language code, so that note text landing in ǂa is
    still reported rather than read as languages.
    """
    code = value.strip().lower()
    if len(code) <= 3 or len(code) % 3:
        return [value]

    chunks = [code[i : i + 3] for i in range(0, len(code), 3)]
    if all(from_code(chunk) is not None for chunk in chunks):
        return chunks

    return [value]


def _resolve(code: str | None) -> Language | None:
    """Codes that are absent, mean "no language", or are suppressed produce nothing."""
    if code is None or _is_no_language(code):
        return None

    language = from_code(code.strip().lower())
    if language is None:
        logger.error("Unrecognised language code", code=code)
        return None

    if language.id in SUPPRESSED_CODES:
        return None

    return language


def _is_no_language(code: str) -> bool:
    """Blanks and MARC fill characters both mean the record has no language, which is
    not a data error: some records legitimately have none, e.g. paintings."""
    return not code.strip("| ")
