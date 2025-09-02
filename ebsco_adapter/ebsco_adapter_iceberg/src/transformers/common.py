from collections.abc import Callable

from pymarc.record import Record


def mandatory_field(marc_code: str, field_name: str) -> Callable:
    """
    Decorator for a field that must be present and non-empty in the input record
    :param marc_code:
    :param field_name:
    :return:
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
