from pymarc.record import Record

from adapters.ebsco.models.work import Language
from adapters.ebsco.transformers.lookups.languages import from_code
from adapters.ebsco.transformers.parsers.field008 import RawField008


def extract_language(record: Record) -> Language | None:
    if field_008 := RawField008.from_record(record):
        return from_code(field_008.languagecode)
    return None


def extract_languages(record: Record) -> list[Language]:
    """
    Although a record can maximally have one language, the Work
    interface defines a list of languages, so this converts
    an optional language into a list of zero-or-one languages.
    """
    if language := extract_language(record):
        return [language]
    return []
