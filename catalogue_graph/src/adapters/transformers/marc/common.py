from collections.abc import Callable, Iterable

from pymarc.record import Record


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


def non_empty_subfields(
    field_code: str, subfield_code: str, record: Record
) -> list[str]:
    return non_empty(
        field.get(subfield_code, "").strip() for field in record.get_fields(field_code)
    )


def first_non_empty_subfield(
    field_code: str, subfield_code: str, record: Record
) -> str | None:
    values = non_empty_subfields(field_code, subfield_code, record)
    return values[0] if values else None


def get_a_subfields(field_code: str, record: Record) -> list[str]:
    return non_empty_subfields(field_code, "a", record)


def non_empty[T](value: Iterable[T | None]) -> list[T]:
    return [value for value in value if value]
