"""
The following fields are used as possible alternative titles, joining all
of their subfields:
* 130 http://www.loc.gov/marc/bibliographic/bd130.html
* 240 https://www.loc.gov/marc/bibliographic/bd240.html
* 242 https://www.loc.gov/marc/bibliographic/bd242.html
* 246 https://www.loc.gov/marc/bibliographic/bd246.html
"""

from collections import OrderedDict

from pymarc.field import Field
from pymarc.record import Record


def extract_alternative_titles(record: Record) -> list[str]:
    title_fields = record.get_fields("130", "240", "242", "246")
    # Trim, filter out empty, and deduplicate based on trimmed values
    raw_titles = [
        format_field(field) for field in title_fields if not is_caption(field)
    ]
    trimmed_titles = [title.strip() for title in raw_titles]
    non_blank_titles = [title for title in trimmed_titles if title]
    return distinct(non_blank_titles)


def format_field(field: Field) -> str:
    """
    Join the field's subfields, omitting those that are not part of the title.

    ǂ5 UkLW is a Wellcome-internal marker, and ǂ6 is a link to an 880 field.
    Other ǂ5 values (e.g. DNLM) are kept.
    """
    return " ".join(
        subfield.value
        for subfield in field.subfields
        if subfield.code != "6"
        and not (subfield.code == "5" and subfield.value == "UkLW")
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
