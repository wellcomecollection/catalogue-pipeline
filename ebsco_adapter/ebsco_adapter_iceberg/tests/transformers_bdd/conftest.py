from __future__ import annotations

import logging
import re
from collections.abc import Sequence
from typing import Any

import pytest
from _pytest.logging import LogCaptureFixture
from pymarc.record import Field, Indicators, Record, Subfield
from pytest_bdd import given, parsers, then, when

from models.work import SourceConcept
from transformers.ebsco_to_weco import transform_record

# mypy: allow-untyped-calls

logger: logging.Logger = logging.getLogger(__name__)

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
}


def _normalise_attr_phrase(attr_phrase: str) -> str:
    key = attr_phrase.strip().lower()
    return ATTR_ALIASES.get(key, key)


def _get_attr_list(parent, attr_phrase: str) -> Any:
    attr_name = _normalise_attr_phrase(attr_phrase)
    return getattr(parent, attr_name)


@pytest.fixture
def context() -> dict[str, Any]:
    return {}


@given("a valid MARC record", target_fixture="marc_record")
def marc_record() -> Record:
    record = Record()
    record.add_field(Field(tag="001", data="test001"))
    record.add_field(
        Field(tag="245", subfields=[Subfield(code="a", value="Test Title")])
    )
    return record


# ------------------------------------------------------------------
# Generic MARC field builder
# ------------------------------------------------------------------
field_step_regex = parsers.re(
    r"the MARC record has (?:a|another) (?P<tag>\d{3}) field"
    r'(?: with indicators "(?P<ind1>[^"])" "(?P<ind2>[^"])"|)'
    r'(?P<subs>(?: (?:with|and) subfield "[^"]+" value "[^"]*")+)'  # one or more subfield/value pairs
)


@given(field_step_regex)
def add_field(
        marc_record: Record,
        tag: str,
        subs: str,
        ind1: str | None = None,
        ind2: str | None = None,
) -> None:
    matches: list[tuple[str, str]] = re.findall(
        r' (?:with|and) subfield "([^"]+)" value "([^"]*)"', subs
    )
    subfields: list[Subfield] = [Subfield(code=c, value=v) for c, v in matches]
    indicators: Indicators | None = Indicators(ind1, ind2) if ind1 and ind2 else None
    marc_record.add_field(Field(tag=tag, indicators=indicators, subfields=subfields))


@given(parsers.re(r"the MARC record has (?:a|another) (?P<tag>\d{3}) field with subfields:"))
def field_from_table(marc_record: Record, datatable: list[list[str]], tag):
    headings = datatable[0]
    code = headings.index("code")
    value = headings.index("value")
    subfields = [Subfield(code=row[code], value=row[value]) for row in datatable[1:]]
    marc_record.add_field(Field(tag=tag, subfields=subfields))


# ------------------------------------------------------------------
# Transformation
# ------------------------------------------------------------------
@when("I transform the MARC record", target_fixture="work")
def do_transform(
        context: dict[str, Any], marc_record: Record
) -> None:
    work = transform_record(marc_record)
    context["result"] = work
    return work


# ------------------------------------------------------------------
# Generic list assertion steps
# ------------------------------------------------------------------


@then(parsers.parse("there are {count:d} {attr_phrase}"))
def list_member_count(work, count: int, attr_phrase: str) -> None:
    values: Sequence[Any] = _get_attr_list(work, attr_phrase)
    assert (
            len(values) == count
    ), f"Expected {count} {attr_phrase}, got {len(values)}: {values}"


@then(parsers.parse("it has {count:d} {attr_phrase}"))
def child_list_member_count(antecedent: Any, count: int, attr_phrase: str) -> None:
    values: Sequence[Any] = _get_attr_list(antecedent, attr_phrase)
    assert (
            len(values) == count
    ), f"Expected {count} {attr_phrase}, got {len(values)}: {values}"


@then(parsers.parse("it has {count:d} {attr_phrase}:"))
def child_list_member_datatable(antecedent: Any, datatable: list[list[str]], count: int, attr_phrase: str) -> None:
    members: Sequence[Any] = _get_attr_list(antecedent, attr_phrase)
    assert (
            len(members) == count
    ), f"Expected {count} {attr_phrase}, got {len(members)}: {members}"
    headings = datatable[0]
    for index, row in enumerate(datatable[1:]):
        member = members[index]
        for (key, expected) in zip(headings, row):
            assert getattr(member, key) == expected


@then(parsers.parse("there are no {attr_phrase}"))
def list_member_empty(work, attr_phrase: str) -> None:
    list_member_count(work, 0, attr_phrase)


@then(parsers.parse('the only {attr_phrase} is "{value}"'))
def list_member_only(work, attr_phrase: str, value: str) -> None:
    list_member_count(work, 1, attr_phrase)
    return list_member_nth_is(work, 1, attr_phrase, value)


def _list_member_nth(parent: Any, index: str, attr_phrase: str) -> Any:
    idx = int(index) - 1
    values: Sequence[Any] = _get_attr_list(parent, attr_phrase)
    assert (
            0 <= idx < len(values)
    ), f"Index {index} out of range (have {len(values)} {attr_phrase}: {values})"
    member = values[idx]
    return member


@then(
    parsers.re(
        r'the (?P<index>\d+)(?:st|nd|rd|th) (?P<attr_phrase>alternative title|alternative titles|designation|designations) is "(?P<value>.*)"'
    )
)
def list_member_nth_is(
        work, index: str, attr_phrase: str, value: str
) -> None:
    nth_member = _list_member_nth(work, index, attr_phrase)
    assert (
            nth_member == value
    ), f"Expected {attr_phrase} at position {index} == {value!r}, got {nth_member!r}"
    return nth_member


@then(parsers.parse('the only {attr_phrase} has the {property} "{value}"'), target_fixture="antecedent")
def only_list_member_has(context, work, attr_phrase: str, property: str, value: str):
    member = _list_member_nth(work, 1, attr_phrase)
    assert getattr(member, property) == value
    context[attr_phrase] = member
    return member


@then(parsers.parse('that {attr_phrase} has the {property} "{value}"'))
def context_has(context, attr_phrase: str, property: str, value: str):
    assert getattr(context[attr_phrase], property) == value


@then(parsers.parse('its only {attr_phrase} has the {property} "{value}"'), target_fixture="antecedent")
def only_list_member_has(context, antecedent, attr_phrase: str, property: str, value: str):
    member = _list_member_nth(antecedent, 1, attr_phrase)
    assert getattr(member, property) == value
    context[attr_phrase] = member


@then(parsers.parse('its identifier value is "{value}"'))
def only_genre_identifier_value(context: dict[str, Any], value: str) -> None:
    genres: list[Any] = getattr(context["result"], "genres", [])
    assert len(genres) == 1, (
        "Step 'its identifier value is ...' assumes exactly one genre; "
        f"found {len(genres)}"
    )
    g = genres[context.get("_last_single_genre_index", 0)]
    assert (
            g.source.value == value
    ), f"Expected identifier value {value!r}, got {g.source.value!r}"


@then(parsers.parse('its identifier type is "{ctype}"'))
def only_genre_identifier_type(context: dict[str, Any], ctype: str) -> None:
    genres: list[Any] = getattr(context["result"], "genres", [])
    assert len(genres) == 1, (
        "Step 'its identifier type is ...' assumes exactly one genre; "
        f"found {len(genres)}"
    )
    g = genres[context.get("_last_single_genre_index", 0)]
    actual = getattr(g.source, "identifierType", None)
    assert isinstance(actual, SourceConcept)
    if hasattr(actual, "value"):
        actual_str = actual.value
    elif hasattr(actual, "name"):
        actual_str = actual.name.title()
    else:
        actual_str = str(actual)
    assert (
            actual_str == ctype
    ), f"Expected identifier type {ctype!r}, got {actual_str!r}"


# ------------- Utility accessors ------------- #
def _get_genres(context: dict[str, Any]) -> list[Any]:
    return getattr(context["result"], "genres", [])


def _assert_single_genre(context: dict[str, Any]) -> Any:
    genres = _get_genres(context)
    assert len(genres) == 1, f"Expected exactly one genre, got {len(genres)}"
    return genres[0]


def _ordinal_index(ord_with_suffix: str) -> int:
    m = re.match(r"(\d+)", ord_with_suffix)
    assert m, f"Unrecognised ordinal: {ord_with_suffix}"
    return int(m.group(1)) - 1


# ------------- New Step Definitions (Genres) ------------- #
@then(parsers.parse("the genre has {count:d} concept"))
@then(parsers.parse("the genre has {count:d} concepts"))
def step_genre_concept_count(context: dict[str, Any], count: int) -> None:
    genre = _assert_single_genre(context)
    actual = len(genre.concepts)
    assert actual == count, f"Expected {count} concepts, got {actual}"


@then(parsers.parse('the concept has an identifier with value "{value}"'))
def step_single_concept_identifier_value(context: dict[str, Any], value: str) -> None:
    genre = _assert_single_genre(context)
    assert (
            len(genre.concepts) == 1
    ), f"Expected exactly one concept for this step, found {len(genre.concepts)}"
    concept = genre.concepts[0]
    assert concept.id is not None, "Concept missing identifier"
    assert (
            concept.id.value == value
    ), f"Expected identifier value {value!r}, got {concept.id.value!r}"


@then(parsers.parse('the identifier\'s ontology type is "{ontology}"'))
def step_concept_identifier_ontology(context: dict[str, Any], ontology: str) -> None:
    genre = _assert_single_genre(context)
    assert (
            len(genre.concepts) == 1
    ), f"Expected exactly one concept for this step, found {len(genre.concepts)}"
    concept = genre.concepts[0]
    assert concept.id is not None, "Concept missing identifier"
    assert (
            concept.id.ontology_type == ontology
    ), f"Expected ontology type {ontology!r}, got {concept.id.ontology_type!r}"


@then(parsers.parse('its identifier\'s identifier type is "{itype}"'))
def step_concept_identifier_identifier_type(
        context: dict[str, Any], itype: str
) -> None:
    genre = _assert_single_genre(context)
    assert (
            len(genre.concepts) == 1
    ), f"Expected exactly one concept for this step, found {len(genre.concepts)}"
    concept = genre.concepts[0]
    assert concept.id is not None, "Concept missing identifier"
    assert (
            concept.id.identifier_type == itype
    ), f"Expected identifier type {itype!r}, got {concept.id.identifier_type!r}"


@then(parsers.re(r'the (?P<ord>\d+\w{2}) genre has the label "(?P<label>.*)"'))
def step_ordinal_genre_label(context: dict[str, Any], ord: str, label: str) -> None:
    genres = _get_genres(context)
    idx = _ordinal_index(ord)
    assert (
            0 <= idx < len(genres)
    ), f"Genre index {idx} out of range (have {len(genres)})"
    actual = genres[idx].label
    assert actual == label, f"Expected genre {ord} label {label!r}, got {actual!r}"


@then(parsers.re(r'the (?P<ord>\d+\w{2}) concept has the label "(?P<label>.*)"'))
def step_ordinal_concept_label(context: dict[str, Any], ord: str, label: str) -> None:
    genre = _assert_single_genre(context)
    idx = _ordinal_index(ord)
    assert (
            0 <= idx < len(genre.concepts)
    ), f"Concept index {idx} out of range (have {len(genre.concepts)})"
    actual = genre.concepts[idx].label
    assert actual == label, f"Expected concept {ord} label {label!r}, got {actual!r}"


@then(parsers.parse('an error "{message}" is logged'))
def step_error_logged(caplog: LogCaptureFixture, message: str) -> None:
    matches = [
        rec
        for rec in caplog.records
        if rec.levelno >= logging.ERROR and rec.getMessage() == message
    ]
    assert matches, (
            f'Expected an ERROR log with message: "{message}". '
            f"Captured log messages were:\n"
            + "\n".join(f"[{r.levelname}] {r.getMessage()}" for r in caplog.records)
    )


@then(parsers.parse('the only genre has a label starting with "{prefix}"'))
def only_genre_label_startswith(context: dict[str, Any], prefix: str) -> None:
    genres: list[Any] = getattr(context["result"], "genres", [])
    assert len(genres) == 1, f"Expected exactly one genre, found {len(genres)}"
    actual = genres[0].label
    assert actual.startswith(
        prefix
    ), f'Expected genre label to start with "{prefix}", got "{actual}"'


@then(parsers.re(r'the (?P<ord>\d+\w{2}) concept has the type "(?P<ctype>.*)"'))
def ordinal_concept_type(context: dict[str, Any], ord: str, ctype: str) -> None:
    m = re.match(r"(\d+)", ord)
    assert m, f"Unrecognised ordinal: {ord}"
    idx = int(m.group(1)) - 1

    genres: list[Any] = getattr(context["result"], "genres", [])
    assert (
            len(genres) == 1
    ), "Ordinal concept type step assumes a single genre in context."
    genre = genres[0]
    assert (
            0 <= idx < len(genre.concepts)
    ), f"Concept index {idx} out of range (have {len(genre.concepts)})"
    actual = genre.concepts[idx].type
    assert actual == ctype, f'Expected {ord} concept type "{ctype}", got "{actual}"'


@given(parsers.parse('that field has a subfield "{code}" with value "{value}"'))
def add_subfield_to_last_field(marc_record: Record, code: str, value: str) -> None:
    """
    Append a subfield to the most recently added field (e.g. a 655).

    Assumes a prior step created the field, e.g.:
      Given the MARC record has a 655 field with subfield "a" value "Disco Polo"
    """
    assert marc_record.fields, "No fields present to append a subfield to."
    marc_record.fields[-1].add_subfield(code, value)


@then(
    parsers.re(
        r'the (?P<ord>\d+\w{2}) concept has the identifier value "(?P<value>.*)"'
    )
)
def step_ordinal_concept_identifier_value(
        context: dict[str, Any], ord: str, value: str
) -> None:
    """
    Assert the Nth concept (ordinal like 1st/2nd/3rd/4th etc.) of the only genre
    has the given identifier value.
    """
    genre = _assert_single_genre(context)  # relies on helper already defined above
    idx = _ordinal_index(ord)
    assert (
            0 <= idx < len(genre.concepts)
    ), f"Concept index {idx} out of range (have {len(genre.concepts)})"
    concept = genre.concepts[idx]
    assert concept.id is not None, f"Concept {ord} is missing an identifier"
    actual = concept.id.value
    assert (
            actual == value
    ), f'Expected {ord} concept identifier value "{value}", got "{actual}"'
