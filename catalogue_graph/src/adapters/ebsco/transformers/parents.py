import logging

from pymarc.record import Record

from models.pipeline.work_state import WorkAncestor

logger: logging.Logger = logging.getLogger(__name__)

SUBFIELD_TAGS: dict[str, list[str]] = {
    "440": ["a"],
    "490": ["a"],
    "773": ["t", "a", "s"],
    "830": ["t", "a"],
}


def get_parents(record: Record) -> list[WorkAncestor]:
    """
    Convert the parent link MARC fields into WorkAncestor objects. Links to a parent are found in four different
    MARC fields These are all to result in a Series Relation with the title taken from the MARC field:
        - 440 - Series Statement/Added Entry-Title (https://www.loc.gov/marc/bibliographic/bd440.html)
        - 490 - Series Statement (https://www.loc.gov/marc/bibliographic/bd490.html)
        - 773 - Host Item Entry (https://www.loc.gov/marc/bibliographic/bd773.html)
        - 830 - Series Added Entry-Uniform Title (https://www.loc.gov/marc/bibliographic/bd830.html

    MARC fields are designed to be output by concatenating the subfields in document order. As such, they may contain
    punctuation (and spacing) intended to be presented in that context specifically. In this usage, we are separating
    the main content from the subfield so this punctuation is not wanted.
    """
    seen_titles = set()
    titles: list[str] = []
    for field in record.get_fields(*SUBFIELD_TAGS.keys()):
        subfields = field.get_subfields(*SUBFIELD_TAGS[field.tag])
        if len(subfields) == 0:
            logger.warning(f"No {field.tag} Series relationship found for {field}.")
            continue

        if len(subfields) > 1:
            logger.warning(
                f"Ambiguous {field.tag} Series relationship found for {field}."
            )

        title = subfields[0].removesuffix(";").removesuffix(",").strip()
        if title and title not in seen_titles:
            seen_titles.add(title)
            titles.append(title)

    return [
        WorkAncestor(
            title=title, work_type="Series", depth=0, num_children=0, num_descendents=0
        )
        for title in titles
    ]
