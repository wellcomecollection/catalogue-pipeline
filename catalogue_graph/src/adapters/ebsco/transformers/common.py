from collections.abc import Callable, Iterable
from urllib.parse import urlparse

from pymarc.record import Field, Record, Subfield

from models.pipeline.concept import Concept
from models.pipeline.identifier import Id, Identifiable, SourceIdentifier
from utils.types import ConceptType


def mandatory_field(marc_code: str, field_name: str) -> Callable:
    """
    Decorator for functions that extract from a field that must be
    present and non-empty in the input record.

    This decorator performs both the entry (is the field there)
    and exit (does it contain anything) checks for an extractor function
    so that you can keep the extractors themselves clean and readable, without
    having to cover them in error-checking boilerplate.
    :param marc_code: The MARC code for the field that must be present
    :param field_name: A human-readable name for this field
    """

    def decorate(extractor: Callable[[Record], str]) -> Callable[[Record], str]:
        def wrap(marc_record: Record) -> str:
            if marc_code not in marc_record:
                raise ValueError(f"Missing {field_name} field ({marc_code})")
            extracted_value = extractor(marc_record)
            if not extracted_value:
                raise ValueError(f"Empty {field_name} field ({marc_code})")
            return extracted_value

        return wrap

    return decorate


def get_a_subfields(field_code: str, record: Record) -> list[str]:
    return non_empty(
        field.get("a", "").strip() for field in record.get_fields(field_code)
    )


def non_empty[T](value: Iterable[T | None]) -> list[T]:
    return [value for value in value if value]


def is_url(maybe_url: str) -> bool:
    """
    A potential URL is only considered a URL for linking purposes if it
    has a webpage-appropriate scheme and would actually go somewhere.

    This test is actually slightly stricter than the old Scala way, which
    would allow jar:// and ftp:// urls, but in the data seen so far
    from EBSCO, all $u subfields are http urls.
    """
    url = urlparse(maybe_url)
    # urlparse is very lenient in what it accepts and parses.
    # e.g. If it's not a fully qualified URL, it is interpreted as relative.
    #
    # So we need to do a bit of extra checking to see if it really
    # looks like a URL.
    return bool(url.scheme in ["http", "https"] and url.netloc)


SUBFIELD_TO_TYPE: dict[str, ConceptType] = {"y": "Period", "z": "Place"}


def subdivision_concepts(
    field: Field, subdivision_subfields: list[str]
) -> list[Concept]:
    return [
        extract_concept_from_subfield(subfield)
        for subfield in field.subfields
        if subfield.code in subdivision_subfields
    ]


def extract_concept_from_subfield(subfield: Subfield) -> Concept:
    return extract_concept_from_subfield_value(subfield.code, subfield.value)


def extract_concept_from_subfield_value(code: str, value: str) -> Concept:
    concept_label = _clean_concept_label(value)
    identifier = SourceIdentifier(
        identifier_type=Id(id="label-derived"),
        ontology_type="Genre",
        value=normalise_identifier_value(concept_label),
    )
    return Concept(
        id=Identifiable.from_source_identifier(identifier),
        label=concept_label,
        type=SUBFIELD_TO_TYPE.get(code, "Concept"),
    )


def _clean_concept_label(value: str) -> str:
    """
    Remove trailing punctuation (.,;:) and trim whitespace for concept labels.
    """
    return value.rstrip(".,;:").strip()


def normalise_identifier_value(label: str) -> str:
    """
    Lowercase & collapse internal whitespace for identifier values.
    """
    return " ".join(label.split()).lower()
