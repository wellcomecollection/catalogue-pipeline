from collections import OrderedDict

from pymarc.field import Field
from pymarc.record import Record


def extract_alternative_titles(record: Record) -> list[str]:
    title_fields = record.get_fields("130", "240", "246")
    return distinct(
        [field.format_field() for field in title_fields if not is_caption(field)]
    )


def distinct(titles: list[str]) -> list[str]:
    """
    Remove any duplicates from the list of titles, preserving order.
    """
    return list(OrderedDict.fromkeys(titles))


def is_caption(field: Field) -> bool:
    """
    On a Varying Form of Title, a second indicator "6" marks it as a Caption Title
    See https://www.loc.gov/marc/bibliographic/bd246.html
    """
    return field.tag == "246" and field.indicator2 == "6"
