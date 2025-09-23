import pytest
from pytest_bdd import scenarios, given, when, then, parsers
from pymarc import Record, Field, Subfield

from transformers.ebsco_to_weco import transform_record
import re

scenarios("features/designation.feature")


@pytest.fixture
def context():
    return {}


@given("a valid MARC record with 001 and 245 fields", target_fixture="marc_record")
def marc_record():
    record = Record()
    # For 001, use data instead of value
    record.add_field(Field(tag="001", data="test001"))
    record.add_field(Field(tag="245", subfields=[Subfield(code="a", value="Test Title")]))
    return record


@given(parsers.re(
    r'the MARC record has (?:a|another) (?P<tag>\d{3}) field(?P<subs>(?: (?:with|and) subfield "[^"]+" value "[^"*]*")+)'
))
def add_field(marc_record, tag, subs):
    matches = re.findall(r' with subfield "([^"]+)" value "([^"]*)"', subs)
    subfields = [Subfield(code=code, value=value) for code, value in matches]
    marc_record.add_field(Field(tag=tag, subfields=subfields))


@when("I transform the MARC record")
def do_transform(context, marc_record):
    context["result"] = transform_record(marc_record)


@then("there is no designation")
def check_no_designation(context):
    assert context["result"].designation == []


@then(parsers.parse('the only designation is "{designation}"'))
def check_single_designation(context, designation):
    designations = context["result"].designation
    assert len(designations) == 1
    assert designations[0] == designation


@then(parsers.parse('the first designation is "{designation}"'))
def check_first_designation(context, designation):
    designations = context["result"].designation
    assert len(designations) >= 1
    assert designations[0] == designation


@then(parsers.parse('the second designation is "{designation}"'))
def check_second_designation(context, designation):
    designations = context["result"].designation
    assert len(designations) >= 2
    assert designations[1] == designation


@then(parsers.parse('there are exactly {count:d} designations'))
def check_exact_designation_count(context, count):
    designations = context["result"].designation
    assert len(designations) == count
