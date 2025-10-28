from pymarc.record import Record

from models.pipeline.work_state import WorkAncestor

SUBFIELD_TAGS: dict[str, list[str]] = {
    "440": ["a"],
    "490": ["a"],
    "773": ["t", "a", "s"],
    "830": ["t", "a"],
}


def get_parents(record: Record) -> list[WorkAncestor]:
    seen_titles = set()
    titles: list[str] = []
    for field in record.get_fields(*SUBFIELD_TAGS.keys()):
        subfields = field.get_subfields(*SUBFIELD_TAGS[field.tag])
        if len(subfields) == 0:
            continue
        if len(subfields) > 1:
            pass

        if subfields[0] not in seen_titles:
            seen_titles.add(subfields[0])
            titles.append(subfields[0])

    return [
        WorkAncestor(
            title=title, work_type="Series", depth=0, num_children=0, num_descendents=0
        )
        for title in titles
    ]
