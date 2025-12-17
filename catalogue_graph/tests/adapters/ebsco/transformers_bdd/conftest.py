from __future__ import annotations

import logging
import re
from collections.abc import Sequence
from typing import Any, cast

import pytest
from _pytest.logging import LogCaptureFixture
from pymarc.record import Field, Indicators, Record, Subfield
from pytest_bdd import parsers, then, when

from adapters.ebsco.transformers.ebsco_to_weco import transform_record
from models.pipeline.identifier import Id
from models.pipeline.source.work import VisibleSourceWork

from tests.gherkin_steps.marc import *
from tests.gherkin_steps.work import *

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


def _get_attr_list(parent: Any, attr_phrase: str) -> Any:
    """Resolve attribute phrase to list-like attribute."""
    attr_name = _normalise_attr_phrase(attr_phrase)
    if hasattr(parent, attr_name):
        return getattr(parent, attr_name)
    return []


@pytest.fixture
def context() -> dict[str, Any]:
    return {}


@given("a valid MARC record", target_fixture="marc_record")
def marc_record() -> Record:
    record = marc_record_with_id(identifier="test001")
    record.add_field(
        Field(tag="245", subfields=[Subfield(code="a", value="Test Title")])
    )
    return record


# ------------------------------------------------------------------
# Transformation
# ------------------------------------------------------------------
@when("I transform the MARC record", target_fixture="work")
def do_transform(context: dict[str, Any], marc_record: Record) -> VisibleSourceWork:
    work = transform_record(marc_record)
    context["result"] = work
    return work


# ------------------------------------------------------------------
# Generic list assertion steps
# ------------------------------------------------------------------


@then(parsers.parse("there is 1 {attr_phrase}"))
def one_list_member(work: VisibleSourceWork, attr_phrase: str) -> None:
    list_member_count(work, 1, attr_phrase)


@then(parsers.parse("there are {count:d} {attr_phrase}"))
def list_member_count(work: VisibleSourceWork, count: int, attr_phrase: str) -> None:
    values: Sequence[Any] = _get_attr_list(work.data, attr_phrase)
    assert len(values) == count, (
        f"Expected {count} {attr_phrase}, got {len(values)}: {values}"
    )


@then(
    parsers.re(
        r'that (?P<thing_name>.+)\'s (?P<ord>(\d+\w{2})|only) concept has the identifier (?P<id_member>(type|value)) "(?P<value>.*)"'
    )
)
def context_concept_identifier_value(
        context: dict[str, Any], thing_name: str, ord: str, id_member: str, value: str
) -> None:
    """
    Assert the Nth concept (ordinal like 1st/2nd/3rd/4th etc.) of the thing
    has the given identifier value.
    """
    thing = context[thing_name]
    idx = _ordinal_index(ord)
    assert 0 <= idx < len(thing.concepts), (
        f"Concept index {idx} out of range (have {len(thing.concepts)})"
    )
    concept = thing.concepts[idx]

    source_identifiers = list(concept.id.get_identifiers())
    assert len(source_identifiers) == 1, (
        "Concept should have exactly one SourceIdentifier"
    )
    source_id = source_identifiers[0]

    assert concept.id is not None, f"Concept {ord} is missing an identifier"
    actual = source_id.value if id_member == "value" else source_id.identifier_type.id
    assert actual == value, (
        f'Expected {ord} concept identifier value "{value}", got "{actual}"'
    )


@then(
    parsers.re(
        r"that (?P<thing_name>.+)\'s (?P<ord>(\d+\w{2})|only) concept has a range from (?P<from_val>[\dT:.Z-]+) to (?P<to_val>[\dT:.Z-]+)"
    )
)
def step_ordinal_range(
        context: dict[str, Any], thing_name: str, ord: str, from_val: str, to_val: str
) -> None:
    thing = context[thing_name]
    idx = _ordinal_index(ord)
    assert 0 <= idx < len(thing.concepts), (
        f"Concept index {idx} out of range (have {len(thing.concepts)})"
    )
    assert thing.concepts[idx].type == "Period"
    actual_from = thing.concepts[idx].range.from_time
    actual_to = thing.concepts[idx].range.to_time
    assert actual_from == from_val
    assert actual_to == to_val


@then(parsers.parse("that {thing} has {count:d} {attr_phrase}"))
def sublist_member_count(
        context: dict[str, Any], thing: str, count: int, attr_phrase: str
) -> None:
    values: Sequence[Any] = _get_attr_list(context[thing], attr_phrase)
    assert len(values) == count, (
        f"Expected {count} {attr_phrase}, got {len(values)}: {values}"
    )


@then(parsers.parse("it has {count:d} {attr_phrase}"))
def child_list_member_count(antecedent: Any, count: int, attr_phrase: str) -> None:
    values: Sequence[Any] = _get_attr_list(antecedent, attr_phrase)
    assert len(values) == count, (
        f"Expected {count} {attr_phrase}, got {len(values)}: {values}"
    )


@then(parsers.parse("it has {count:d} {attr_phrase}:"))
def child_list_member_datatable(
        antecedent: Any, datatable: list[list[str]], count: int, attr_phrase: str
) -> None:
    members: Sequence[Any] = _get_attr_list(antecedent, attr_phrase)
    assert len(members) == count, (
        f"Expected {count} {attr_phrase}, got {len(members)}: {members}"
    )
    headings = datatable[0]
    for index, row in enumerate(datatable[1:]):
        member = members[index]
        for key, expected in zip(headings, row, strict=False):
            if "." in key:
                here = member
                for subkey in key.split("."):
                    here = getattr(here, subkey)
                assert here == expected
            else:
                value = getattr(member, key)
                # Allow trailing period retention for Place subdivision under new rules.
                if key == "label" and expected.endswith("."):
                    assert value == expected
                else:
                    assert value == expected


@then(parsers.parse("there are no {attr_phrase}"))
def list_member_empty(work: VisibleSourceWork, attr_phrase: str) -> None:
    list_member_count(work, 0, attr_phrase)


@then(parsers.parse('the only {attr_phrase} is "{value}"'))
def list_member_only(work: VisibleSourceWork, attr_phrase: str, value: str) -> Any:
    list_member_count(work, 1, attr_phrase)
    return list_member_nth_is(work, 1, attr_phrase, value)


def _list_member_nth(parent: Any, index: str | int, attr_phrase: str) -> Any:
    idx = int(index) - 1
    values: Sequence[Any] = _get_attr_list(parent, attr_phrase)
    assert 0 <= idx < len(values), (
        f"Index {index} out of range (have {len(values)} {attr_phrase}: {values})"
    )
    member = values[idx]
    return member


@then(
    parsers.re(
        r'the (?P<index>\d+)(?:st|nd|rd|th) (?P<attr_phrase>alternative title|alternative titles|designation|designations) is "(?P<value>.*)"'
    )
)
def list_member_nth_is(
        work: VisibleSourceWork, index: str | int, attr_phrase: str, value: str
) -> Any:
    nth_member = _list_member_nth(work.data, index, attr_phrase)
    assert nth_member == value, (
        f"Expected {attr_phrase} at position {index} == {value!r}, got {nth_member!r}"
    )
    return nth_member


@then(
    parsers.parse('the only {attr_phrase} has the {property} "{value}"'),
    target_fixture="antecedent",
)
def only_root_list_member_has(
        context: dict[str, Any],
        work: VisibleSourceWork,
        attr_phrase: str,
        property: str,
        value: str,
) -> Any:
    list_member_count(work, 1, attr_phrase)
    member = _list_member_nth(work.data, 1, attr_phrase)
    assert getattr(member, property) == value
    context[attr_phrase] = member
    return member


@then(parsers.re(r'that (?P<thing_name>.+) has the (?P<property>.+) "(?P<value>.*)"'))
def context_has(
        context: dict[str, Any], thing_name: str, property: str, value: str
) -> None:
    assert getattr(context[thing_name], property) == value


@then(
    parsers.re(
        r'that (?P<thing_name>.+)\'s (?P<ord>(\d+\w{2})|only) concept has the (?P<property>.+) "(?P<value>.*)"'
    )
)
def context_concept_value(
        context: dict[str, Any], thing_name: str, ord: str, property: str, value: str
) -> None:
    thing = context[thing_name]
    concept = thing.concepts[_ordinal_index(ord)]
    assert getattr(concept, property) == value
    context["concept"] = concept


@then(
    parsers.parse('its only {attr_phrase} has the {property} "{value}"'),
    target_fixture="antecedent",
)
def only_list_member_has(
        context: dict[str, Any],
        antecedent: Any,
        attr_phrase: str,
        property: str,
        value: str,
) -> None:
    # Callers should pass .data if required; use antecedent directly.
    member = _list_member_nth(antecedent, 1, attr_phrase)
    assert getattr(member, property) == value
    context[attr_phrase] = member


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


# ------------- Utility accessors ------------- #
def _get_genres(context: dict[str, Any]) -> list[Any]:
    # Cast context['result'] to VisibleSourceWork to satisfy mypy; runtime guarantees this via do_transform
    work = cast(VisibleSourceWork, context["result"])
    return work.data.genres


def _assert_single_genre(context: dict[str, Any]) -> Any:
    genres = _get_genres(context)
    assert len(genres) == 1, f"Expected exactly one genre, got {len(genres)}"
    return genres[0]


def _ordinal_index(ord_with_suffix: str) -> int:
    if ord_with_suffix == "only":
        return 0
    m = re.match(r"(\d+)", ord_with_suffix)
    assert m, f"Unrecognised ordinal: {ord_with_suffix}"
    return int(m.group(1)) - 1


# ------------- New Step Definitions (Genres) ------------- #


@then(parsers.parse('the concept has an identifier with value "{value}"'))
def step_single_concept_identifier_value(context: dict[str, Any], value: str) -> None:
    genre = _assert_single_genre(context)
    assert len(genre.concepts) == 1, (
        f"Expected exactly one concept for this step, found {len(genre.concepts)}"
    )
    concept = genre.concepts[0]
    assert concept.id is not None, "Concept missing identifier"
    source_identifiers = list(concept.id.get_identifiers())
    assert len(source_identifiers) == 1, (
        "Concept should have exactly one SourceIdentifier"
    )
    source_id = source_identifiers[0]
    assert source_id.value == value, (
        f"Expected identifier value {value!r}, got {source_id.value!r}"
    )


@then(parsers.parse('the identifier\'s ontology type is "{ontology}"'))
def step_concept_identifier_ontology(context: dict[str, Any], ontology: str) -> None:
    genre = _assert_single_genre(context)
    assert len(genre.concepts) == 1, (
        f"Expected exactly one concept for this step, found {len(genre.concepts)}"
    )
    concept = genre.concepts[0]
    assert concept.id is not None, "Concept missing identifier"
    source_identifiers = list(concept.id.get_identifiers())
    assert len(source_identifiers) == 1, (
        "Concept should have exactly one SourceIdentifier"
    )
    source_id = source_identifiers[0]
    assert source_id.ontology_type == ontology, (
        f"Expected ontology type {ontology!r}, got {source_id.ontology_type!r}"
    )


@then(parsers.parse('its identifier\'s identifier type is "{itype}"'))
def step_concept_identifier_identifier_type(
        context: dict[str, Any], itype: str
) -> None:
    genre = _assert_single_genre(context)
    assert len(genre.concepts) == 1, (
        f"Expected exactly one concept for this step, found {len(genre.concepts)}"
    )
    concept = genre.concepts[0]
    source_identifiers = list(concept.id.get_identifiers())
    assert len(source_identifiers) == 1, (
        "Concept should have exactly one SourceIdentifier"
    )
    source_id = source_identifiers[0]

    assert source_id.identifier_type == Id(id=itype), (
        f"Expected identifier type {itype!r}, got {source_id.identifier_type!r}"
    )


@then(parsers.re(r'the (?P<ord>\d+\w{2}) genre has the label "(?P<label>.*)"'))
def step_ordinal_genre_label(context: dict[str, Any], ord: str, label: str) -> None:
    genres = _get_genres(context)
    idx = _ordinal_index(ord)
    assert 0 <= idx < len(genres), (
        f"Genre index {idx} out of range (have {len(genres)})"
    )
    actual = genres[idx].label
    assert actual == label, f"Expected genre {ord} label {label!r}, got {actual!r}"


@then(parsers.re(r'the (?P<ord>\d+\w{2}) concept has the label "(?P<label>.*)"'))
def step_ordinal_concept_label(context: dict[str, Any], ord: str, label: str) -> None:
    genre = _assert_single_genre(context)
    idx = _ordinal_index(ord)
    assert 0 <= idx < len(genre.concepts), (
        f"Concept index {idx} out of range (have {len(genre.concepts)})"
    )
    actual = genre.concepts[idx].label
    # New semantics: no implicit period trimming for Place; expected label in test fixtures updated accordingly.
    assert actual == label, f"Expected concept {ord} label {label!r}, got {actual!r}"


@then(parsers.parse('the only genre has a label starting with "{prefix}"'))
def only_genre_label_startswith(context: dict[str, Any], prefix: str) -> None:
    genres = context["result"].data.genres
    assert len(genres) == 1, f"Expected exactly one genre, found {len(genres)}"
    actual = genres[0].label
    assert actual.startswith(prefix), (
        f'Expected genre label to start with "{prefix}", got "{actual}"'
    )


@then(parsers.re(r'the (?P<ord>\d+\w{2}) concept has the type "(?P<ctype>.*)"'))
def ordinal_concept_type(context: dict[str, Any], ord: str, ctype: str) -> None:
    m = re.match(r"(\d+)", ord)
    assert m, f"Unrecognised ordinal: {ord}"
    idx = int(m.group(1)) - 1
    genres = context["result"].data.genres
    assert len(genres) == 1, (
        "Ordinal concept type step assumes a single genre in context."
    )
    genre = genres[0]
    assert 0 <= idx < len(genre.concepts), (
        f"Concept index {idx} out of range (have {len(genre.concepts)})"
    )
    actual = genre.concepts[idx].type
    assert actual == ctype, f'Expected {ord} concept type "{ctype}", got "{actual}"'
