from typing import Any, Sequence
from pytest_bdd import then, parsers
from models.pipeline.source.work import SourceWork

# ------------------------------------------------------------------
# Attribute phrase -> model attribute mapping (extendable)
# ------------------------------------------------------------------
ATTR_ALIASES: dict[str, str] = {
    "designation": "designation",
    "designations": "designation",
    "alternative title": "alternative_titles",
    "alternative titles": "alternative_titles",
    "genre": "genres",
    "subject": "subjects",
    "concept": "concepts",
    "other identifier": "other_identifiers",
    "note": "notes",
}


def _normalise_attr_phrase(attr_phrase: str) -> str:
    key = attr_phrase.strip().lower()
    return ATTR_ALIASES.get(key, key.replace(" ", "_"))


def _get_attr_list(parent: Any, attr_phrase: str) -> Any:
    """Resolve attribute phrase to list-like attribute."""
    attr_name = _normalise_attr_phrase(attr_phrase)
    if hasattr(parent, attr_name):
        return getattr(parent, attr_name)
    return []


def _list_member_nth(parent: Any, index: str | int, attr_phrase: str) -> Any:
    idx = int(index) - 1
    values: Sequence[Any] = _get_attr_list(parent, attr_phrase)
    assert 0 <= idx < len(values), (
        f"Index {index} out of range (have {len(values)} {attr_phrase}: {values})"
    )
    member = values[idx]
    return member


@then('the work is invisible')
def the_work_is_invisible(work: SourceWork) -> None:
    assert work.type == "Invisible"


@then(parsers.parse('the work has the identifier "{identifier}"'))
def the_work_has_the_identifier(work: SourceWork, identifier: str) -> None:
    assert work.state is not None
    assert work.state.id() == identifier


@then(parsers.parse("the work's source modified time is {date_str}"))
def work_last_modified_date(
        work: SourceWork, date_str: str
) -> None:
    assert work.state.source_modified_time == date_str


@then(parsers.parse("the work's title is {title}"))
def work_title(
        work: SourceWork, title: str
) -> None:
    assert work.data.title == title


# ------------------------------------------------------------------
# Generic list assertion steps
# ------------------------------------------------------------------


@then(parsers.parse("there is 1 {attr_phrase}"))
def one_list_member(work: SourceWork, attr_phrase: str) -> None:
    list_member_count(work, 1, attr_phrase)


@then(parsers.parse("there are {count:d} {attr_phrase}"))
def list_member_count(work: SourceWork, count: int, attr_phrase: str) -> None:
    values: Sequence[Any] = _get_attr_list(work.data, attr_phrase)
    assert len(values) == count, (
        f"Expected {count} {attr_phrase}, got {len(values)}: {values}"
    )


@then(parsers.parse("there are no {attr_phrase}"))
def list_member_empty(work: SourceWork, attr_phrase: str) -> None:
    list_member_count(work, 0, attr_phrase)


@then(parsers.parse('the only {attr_phrase} is "{value}"'))
def list_member_only(work: SourceWork, attr_phrase: str, value: str) -> Any:
    list_member_count(work, 1, attr_phrase)
    return list_member_nth_is(work, 1, attr_phrase, value)


@then(parsers.parse('the only {attr_phrase} has the {sub_attr} "{value}"'))
def list_member_has(work: SourceWork, attr_phrase: str, sub_attr: str, value: str) -> Any:
    list_member_count(work, 1, attr_phrase)
    return list_member_nth_has(work, 1, attr_phrase, sub_attr, value)


@then(
    parsers.re(
        r'the (?P<index>\d+)(?:st|nd|rd|th) (?P<attr_phrase>.*) is "(?P<value>.*)"'
    )
)
def list_member_nth_is(
        work: SourceWork, index: str | int, attr_phrase: str, value: str
) -> Any:
    nth_member = _list_member_nth(work.data, index, attr_phrase)
    assert nth_member == value, (
        f"Expected {attr_phrase} at position {index} == {value!r}, got {nth_member!r}"
    )
    return nth_member


@then(
    parsers.re(
        r'the (?P<index>\d+)(?:st|nd|rd|th) (?P<attr_phrase>.*) has the (?P<sub_attr>.*) "(?P<value>.*)"'
    )
)
def list_member_nth_has(
        work: SourceWork, index: str | int, attr_phrase: str, sub_attr: str, value: str
) -> Any:
    nth_member = _list_member_nth(work.data, index, attr_phrase)
    actual = drill_through_dots(nth_member, sub_attr)
    assert actual == value, (
        f"Expected {attr_phrase}.{sub_attr} at position {index} == {value!r}, got {actual!r}"
    )
    return nth_member


def drill_through_dots(obj: Any, path: str) -> Any:
    parts = path.split(".")
    current = obj
    for part in parts:
        current = getattr(current, part)
    return current


@then(parsers.parse("the work has {count:d} {attr_phrase} with {sub_attr}:"))
def child_list_member_has_with_datatable(
        work: SourceWork, datatable: list[list[str]], count: int, attr_phrase: str, sub_attr: str
) -> None:
    members: Sequence[Any] = _get_attr_list(work.data, attr_phrase)
    assert len(members) == count, (
        f"Expected {count} {attr_phrase}, got {len(members)}: {members}"
    )
    for member, row in zip(members, datatable):
        actual = drill_through_dots(member, sub_attr)
        assert actual == row[0]


@then(parsers.re(r"the work has (?P<count>\d+) (?P<atttr_phrase>(?!.*\bwith\b).*):"))
def child_list_member_with_datatable(
        work: SourceWork, datatable: list[list[str]], count: int, attr_phrase: str
) -> None:
    members: Sequence[Any] = _get_attr_list(work.data, attr_phrase)
    assert len(members) == count, (
        f"Expected {count} {attr_phrase}, got {len(members)}: {members}"
    )
    for member, row in zip(members, datatable):
        assert member == row[0]
