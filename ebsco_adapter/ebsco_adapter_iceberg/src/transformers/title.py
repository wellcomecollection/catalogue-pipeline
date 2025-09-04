"""
Title is a mandatory element in a record.
It is found in a datafield with code 245
It is constructed by concatenating specific subfields
EBSCO data often contains a redundant "[electronic resource]" text fragment, which is to be excluded
"""

from pymarc.record import Record

from transformers.common import mandatory_field


@mandatory_field("245", "title")
def extract_title(marc_record: Record) -> str:
    return normalise_whitespace(
        remove_unwanted_text(
            " ".join(marc_record["245"].get_subfields("a", "b", "c", "h", "n", "p"))
        )
    )


def remove_unwanted_text(title: str) -> str:
    return title.replace("[electronic resource]", "")


def normalise_whitespace(string_with_spaces: str) -> str:
    return " ".join(string_with_spaces.split())
