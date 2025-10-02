"""
The following fields are used as possible alternative titles:
* 130 $a http://www.loc.gov/marc/bibliographic/bd130.html
* 240 $a https://www.loc.gov/marc/bibliographic/bd240.html
* 246 $a https://www.loc.gov/marc/bibliographic/bd246.html
"""

from collections import OrderedDict

from pymarc.field import Field
from pymarc.record import Record

from transformers.common import non_empty


def extract_alternative_titles(record: Record) -> list[str]:
    title_fields = record.get_fields("130", "240", "246")
    # Trim, filter out empty, and deduplicate based on trimmed values
    raw_titles = [field.format_field() for field in title_fields if not is_caption(field)]
    trimmed_titles = [title.strip() for title in raw_titles]
    non_blank_titles = [title for title in trimmed_titles if title]
    return distinct(non_blank_titles)


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
