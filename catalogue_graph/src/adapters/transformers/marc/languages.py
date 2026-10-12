"""
Extracting languages from the MARC language code in 008/35-37, plus additional
languages from 041. EBSCO works carry only the primary language; FOLIO works
carry both.

https://www.loc.gov/marc/bibliographic/bd008a.html
https://www.loc.gov/marc/bibliographic/bd041.html
"""

import structlog
from pymarc.record import Record

from adapters.transformers.marc.parsers.field008 import RawField008
from lookups.languages import from_code, is_obsolete
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
    field_008 = RawField008.from_record(record)
    if field_008 is None:
        return None
    return _resolve(field_008.languagecode, tag="008")


def extract_languages(record: Record) -> list[Language]:
    """The primary language first, then 041 ǂa in document order, deduplicated by label."""
    languages: dict[str, Language] = {}
    primary = extract_primary_language(record)
    if primary is not None:
        languages[primary.label] = primary

    for field in record.get_fields("041"):
        for code in field.get_subfields("a"):
            language = _resolve(code, tag="041")
            if language is not None:
                _add(languages, language)

    return list(languages.values())


def _add(languages: dict[str, Language], language: Language) -> None:
    """One language per label, preferring a current code over an obsolete one."""
    existing = languages.get(language.label)
    if existing is None or (is_obsolete(existing.id) and not is_obsolete(language.id)):
        languages[language.label] = language


def _resolve(code: str, tag: str) -> Language | None:
    """Codes that mean "no language" or are suppressed produce nothing."""
    if _is_no_language(code):
        return None

    language = from_code(code.strip().lower())
    if language is None:
        logger.error("Unrecognised language code", code=code, tag=tag)
        return None

    if language.id in SUPPRESSED_CODES:
        return None

    return language


def _is_no_language(code: str) -> bool:
    """Blanks and MARC fill characters both mean the record has no language, which is
    not a data error: some records legitimately have none, e.g. paintings."""
    return not code.strip("| ")
