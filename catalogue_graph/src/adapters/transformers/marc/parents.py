"""
Conceptually, A work may link either to another Work, or to a "Series", which is not
an entity in its own right, but simply a title under which multiple Works may be listed.
See https://github.com/wellcomecollection/docs/tree/main/rfcs/045-sierra-work-relationships.

At present, all parent links behave as though they link to a Series.
"""

from collections.abc import Sequence

import structlog
from pymarc.record import Record

from models.pipeline.work_state import WorkAncestor

logger = structlog.get_logger(__name__)

SUBFIELD_TAGS: dict[str, list[str]] = {
    "440": ["a"],
    "490": ["a"],
    "773": ["t", "a", "s"],
    "830": ["t", "a"],
}


def get_parents(
    record: Record, tag_order: Sequence[str] | None = None
) -> list[WorkAncestor]:
    """
    Parent links are found in four different MARC fields, all of which result in a Series relation with the title taken
    from the MARC field:
        - 440: Series Statement/Added Entry-Title (https://www.loc.gov/marc/bibliographic/bd440.html)
        - 490: Series Statement (https://www.loc.gov/marc/bibliographic/bd490.html)
        - 773: Host Item Entry (https://www.loc.gov/marc/bibliographic/bd773.html)
        - 830: Series Added Entry-Uniform Title (https://www.loc.gov/marc/bibliographic/bd830.html

    MARC fields are designed to be output by concatenating the subfields in document order. As such, they may contain
    punctuation (and spacing) intended to be presented in that context specifically. In this usage, we are separating
    the main content from the subfield so this punctuation is not wanted.

    Parents are returned in the order their fields appear in the record, unless
    `tag_order` lists the tags in the order they should be taken instead.
    """
    fields = record.get_fields(*SUBFIELD_TAGS.keys())
    if tag_order is not None:
        fields = sorted(fields, key=lambda field: tag_order.index(field.tag))

    seen_titles = set()
    titles: list[str] = []
    for field in fields:
        # Subfield codes are listed in priority order. A 773 host item entry normally
        # carries the host's author in ǂa and its title in ǂt, so ǂt must win
        # regardless of where it appears in the field. Scala also warns when ǂa and
        # ǂt appear together, but that is standard cataloguing and would only add
        # noise, so the ambiguity warning below covers a repeated winning code only.
        subfields: list[str] = []
        for code in SUBFIELD_TAGS[field.tag]:
            subfields = field.get_subfields(code)
            if subfields:
                break

        if len(subfields) == 0:
            logger.warning(
                "No Series relationship found", tag=field.tag, field=str(field)
            )
            continue

        if len(subfields) > 1:
            logger.warning(
                "Ambiguous Series relationship found",
                tag=field.tag,
                field=str(field),
                count=len(subfields),
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
