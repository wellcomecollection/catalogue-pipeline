import re
from pymarc.record import Field, Indicators, Record, Subfield
from pytest_bdd import given, parsers


@given("a valid MARC record", target_fixture="marc_record")
def marc_record() -> Record:
    return marc_record_with_id(identifier="test001")


@given(parsers.parse('a MARC record with field 001 "{identifier}"'), target_fixture="marc_record")
def marc_record_with_id(identifier: str) -> Record:
    record = Record()
    record.add_field(Field(tag="001", data=identifier))
    record.add_field(
        Field(tag="245", subfields=[Subfield(code="a", value="Test Title")])
    )
    return record


# ------------------------------------------------------------------
# Generic MARC field builder
# ------------------------------------------------------------------
field_step_regex = parsers.re(
    r"the MARC record has (?:a|another) (?P<tag>\d{3}) field"
    r'(?: with indicators "(?P<ind1>[^"]?)" "(?P<ind2>[^"]?)"|)'
    r'(?P<subs>(?: (?:with|and) subfield "[^"]+" value "[^"]*")+)'  # one or more subfield/value pairs
)


@given(field_step_regex)
def add_field(
        marc_record: Record,
        tag: str,
        subs: str,
        ind1: str = "",
        ind2: str = "",
) -> None:
    matches: list[tuple[str, str]] = re.findall(
        r' (?:with|and) subfield "([^"]+)" value "([^"]*)"', subs
    )
    subfields: list[Subfield] = [Subfield(code=c, value=v) for c, v in matches]
    indicators: Indicators | None = Indicators(ind1, ind2) if ind1 or ind2 else None
    marc_record.add_field(Field(tag=tag, indicators=indicators, subfields=subfields))


@given(
    parsers.re(
        r'the MARC record has (?:a|another) (?P<tag>\d{3}) field(?: with indicators "(?P<ind1>[^"]?)" "(?P<ind2>[^"]?)")? with subfields:'
    )
)
def field_from_table(
        marc_record: Record,
        datatable: list[list[str]],
        tag: str,
        ind1: str = "",
        ind2: str = "",
) -> None:
    headings = datatable[0]
    code = headings.index("code")
    value = headings.index("value")
    subfields = [Subfield(code=row[code], value=row[value]) for row in datatable[1:]]
    indicators: Indicators | None = Indicators(ind1, ind2) if ind1 or ind2 else None
    marc_record.add_field(Field(tag=tag, indicators=indicators, subfields=subfields))


@given(parsers.parse('that field has a subfield "{code}" with value "{value}"'))
def add_subfield_to_last_field(marc_record: Record, code: str, value: str) -> None:
    """
    Append a subfield to the most recently added field (e.g. a 655).

    Assumes a prior step created the field, e.g.:
      Given the MARC record has a 655 field with subfield "a" value "Disco Polo"
    """
    assert marc_record.fields, "No fields present to append a subfield to."
    marc_record.fields[-1].add_subfield(code, value)
