from collections.abc import Sequence
from typing import Any

import pytest
from pytest_bdd import parsers, then

from models.pipeline.location import PhysicalLocation
from models.pipeline.source.work import SourceWork, VisibleSourceWork


@pytest.fixture
def context() -> dict[str, Any]:
    """Shared pytest-bdd context.

    Individual adapter test suites can override this fixture in their own conftest
    if they need stricter typing or additional pre-population.
    """

    return {}


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
    "item": "items",
    "contributor": "contributors",
    "production": "production",
    "productions": "production",
    "date": "dates",
    "place": "places",
    "agent": "agents",
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


@then("the work is invisible")
def the_work_is_invisible(work: SourceWork) -> None:
    assert work.type == "Invisible"


@then("the work is visible")
def the_work_is_visible(work: SourceWork) -> None:
    assert work.type == "Visible"


@then(parsers.parse('the work has the identifier "{identifier}"'))
def the_work_has_the_identifier(work: SourceWork, identifier: str) -> None:
    assert work.state is not None
    assert work.state.id() == identifier


@then(parsers.parse('the work has the predecessor identifier "{identifier}"'))
def the_work_has_the_predecessor_identifier(work: SourceWork, identifier: str) -> None:
    assert work.state is not None
    assert work.state.predecessor_id() == identifier


@then(parsers.parse("the work's source modified time is {date_str}"))
def work_last_modified_date(work: SourceWork, date_str: str) -> None:
    assert work.state.source_modified_time == date_str


@then(parsers.parse("the work's title is {title}"))
def work_title(work: SourceWork, title: str) -> None:
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


@then(parsers.parse("it has {count:d} {attr_phrase}"))
def child_list_member_count(antecedent: Any, count: int, attr_phrase: str) -> None:
    values: Sequence[Any] = _get_attr_list(antecedent, attr_phrase)
    assert len(values) == count, (
        f"Expected {count} {attr_phrase}, got {len(values)}: {values}"
    )


@then(parsers.parse('it has the {sub_attr} "{value}"'))
def antecedent_has_attr(antecedent: Any, sub_attr: str, value: str) -> None:
    actual = drill_through_dots(antecedent, sub_attr)
    assert actual == value, f"Expected {sub_attr} == {value!r}, got {actual!r}"


@then(parsers.parse('the only {attr_phrase} is "{value}"'), target_fixture="antecedent")
def list_member_only(
    context: dict[str, Any], work: SourceWork, attr_phrase: str, value: str
) -> Any:
    list_member_count(work, 1, attr_phrase)
    member = list_member_nth_is(work, 1, attr_phrase, value)
    context[attr_phrase.strip().lower()] = member
    return member


@then(
    parsers.parse('the only {attr_phrase} has the {sub_attr} "{value}"'),
    target_fixture="antecedent",
)
def list_member_has(
    context: dict[str, Any],
    work: SourceWork,
    attr_phrase: str,
    sub_attr: str,
    value: str,
) -> Any:
    list_member_count(work, 1, attr_phrase)
    member = list_member_nth_has(work, 1, attr_phrase, sub_attr, value)
    context[attr_phrase.strip().lower()] = member
    return member


@then(
    parsers.parse('its only {attr_phrase} has the {sub_attr} "{value}"'),
    target_fixture="antecedent",
)
def its_only_list_member_has(
    context: dict[str, Any],
    antecedent: Any,
    attr_phrase: str,
    sub_attr: str,
    value: str,
) -> Any:
    member = _list_member_nth(antecedent, 1, attr_phrase)
    actual = drill_through_dots(member, sub_attr)
    assert actual == value, (
        f"Expected only {attr_phrase}.{sub_attr} == {value!r}, got {actual!r}"
    )
    context[attr_phrase.strip().lower()] = member
    return member


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
    work: SourceWork,
    datatable: list[list[str]],
    count: int,
    attr_phrase: str,
    sub_attr: str,
) -> None:
    members: Sequence[Any] = _get_attr_list(work.data, attr_phrase)
    assert len(members) == count, (
        f"Expected {count} {attr_phrase}, got {len(members)}: {members}"
    )
    for member, row in zip(members, datatable, strict=True):
        actual = drill_through_dots(member, sub_attr)
        assert actual == row[0]


@then(parsers.re(r"the work has (?P<count>\d+) (?P<attr_phrase>(?!.*\bwith\b).*):"))
def child_list_member_with_datatable(
    work: SourceWork, datatable: list[list[str]], count: str, attr_phrase: str
) -> None:
    members: Sequence[Any] = _get_attr_list(work.data, attr_phrase)
    assert len(members) == int(count), (
        f"Expected {count} {attr_phrase}, got {len(members)}: {members}"
    )
    for member, row in zip(members, datatable, strict=True):
        assert member == row[0]


@then(parsers.parse('the work\'s {attr} is "{value}"'))
def work_attr_is(work: SourceWork, attr: str, value: str) -> None:
    actual = drill_through_dots(work.data, attr)
    assert actual == value, f"Expected work.data.{attr} == {value!r}, got {actual!r}"


@then(parsers.parse("the work's {attr} is absent"))
def work_attr_is_absent(work: SourceWork, attr: str) -> None:
    actual = drill_through_dots(work.data, attr)
    assert actual is None, f"Expected work.data.{attr} to be absent, got {actual!r}"


@then("the item is in closed stores")
def item_in_closed_stores(work: VisibleSourceWork) -> None:
    item = work.data.items[0]
    assert len(item.locations) == 1
    loc = item.locations[0]
    assert isinstance(loc, PhysicalLocation)
    assert loc.location_type.id == "closed-stores"
    assert loc.label == "Closed stores"


@then("the item has no access conditions")
def item_no_access_conditions(work: VisibleSourceWork) -> None:
    loc = work.data.items[0].locations[0]
    assert loc.access_conditions == []


@then(parsers.parse('the item has 1 access condition with status "{status}"'))
def item_one_access_condition_with_status(work: VisibleSourceWork, status: str) -> None:
    loc = work.data.items[0].locations[0]
    assert len(loc.access_conditions) == 1
    condition = loc.access_conditions[0]
    assert condition.status is not None
    assert condition.status.type == status
