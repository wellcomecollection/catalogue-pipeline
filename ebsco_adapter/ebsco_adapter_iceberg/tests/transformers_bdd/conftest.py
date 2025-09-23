import re
import pytest
from pytest_bdd import given, when, then, parsers
from pymarc import Record, Field, Subfield, Indicators

from transformers.ebsco_to_weco import transform_record

# ------------------------------------------------------------------
# Attribute phrase -> model attribute mapping (extendable)
# ------------------------------------------------------------------
ATTR_ALIASES = {
    "designation": "designation",
    "designations": "designation",
    "alternative title": "alternative_titles",
    "alternative titles": "alternative_titles",
}


def _normalise_attr_phrase(attr_phrase: str) -> str:
    key = attr_phrase.strip().lower()
    if key not in ATTR_ALIASES:
        raise AssertionError(
            f"Unknown attribute phrase '{attr_phrase}'. "
            f"Known: {', '.join(sorted(ATTR_ALIASES))}"
        )
    return ATTR_ALIASES[key]


def _get_attr_list(context, attr_phrase: str):
    attr_name = _normalise_attr_phrase(attr_phrase)
    return getattr(context["result"], attr_name)


# ------------------------------------------------------------------
# Fixtures
# ------------------------------------------------------------------
@pytest.fixture
def context():
    return {}


@given("a valid MARC record", target_fixture="marc_record")
def marc_record():
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
    r'(?P<subs>(?: with subfield "[^"]+" value "[^"]*")+)'  # one or more subfield/value pairs
)


@given(field_step_regex)
def add_field(marc_record, tag, subs, ind1=None, ind2=None):
    matches = re.findall(r' with subfield "([^"]+)" value "([^"]*)"', subs)
    subfields = [Subfield(code=c, value=v) for c, v in matches]
    indicators = Indicators(ind1, ind2) if ind1 and ind2 else None
    marc_record.add_field(Field(tag=tag, indicators=indicators, subfields=subfields))


# ------------------------------------------------------------------
# Transformation
# ------------------------------------------------------------------
@when("I transform the MARC record")
def do_transform(context, marc_record):
    context["result"] = transform_record(marc_record)


# ------------------------------------------------------------------
# Generic list assertion steps
# ------------------------------------------------------------------


# there are {count} designations / there are {count} alternative titles
@then(parsers.parse("there are {count:d} {attr_phrase}"))
def generic_count(context, count, attr_phrase):
    values = _get_attr_list(context, attr_phrase)
    assert (
        len(values) == count
    ), f"Expected {count} {attr_phrase}, got {len(values)}: {values}"


# there are no alternative titles / there are no designations
@then(parsers.parse("there are no {attr_phrase}"))
def generic_none(context, attr_phrase):
    values = _get_attr_list(context, attr_phrase)
    assert len(values) == 0, f"Expected no {attr_phrase}, got {values}"


# the only alternative title is "..." / the only designation is "..."
@then(parsers.parse('the only {attr_phrase} is "{value}"'))
def generic_only(context, attr_phrase, value):
    # Accept singular phrase preferred here (but mapping handles plural too)
    values = _get_attr_list(context, attr_phrase)
    assert (
        len(values) == 1 and values[0] == value
    ), f"Expected only {attr_phrase} '{value}', got {values}"


# the 1st alternative title is "..." / the 2nd designation is "..."
@then(
    parsers.re(
        r'the (?P<index>\d+)(?:st|nd|rd|th) (?P<attr_phrase>alternative title|alternative titles|designation|designations) is "(?P<value>.*)"'
    )
)
def generic_ordinal(context, index, attr_phrase, value):
    idx = int(index) - 1
    values = _get_attr_list(context, attr_phrase)
    assert (
        0 <= idx < len(values)
    ), f"Index {index} out of range (have {len(values)} {attr_phrase}: {values})"
    assert (
        values[idx] == value
    ), f"Expected {attr_phrase} at position {index} == {value!r}, got {values[idx]!r}"
