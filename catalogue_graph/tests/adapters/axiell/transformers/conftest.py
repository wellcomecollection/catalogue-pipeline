from __future__ import annotations

from pymarc.record import Field, Record, Subfield
from pytest_bdd import given, when

from adapters.axiell.transformers.axiell_to_weco import transform_record
from models.pipeline.source.work import InvisibleSourceWork

# Allow * imports, pulling in individual step definitions is unwieldy
# ruff: noqa: F403, F405
from tests.gherkin_steps.marc import *
from tests.gherkin_steps.work import *

# mypy: allow-untyped-calls


@given("a valid MARC record", target_fixture="marc_record")
def marc_record() -> Record:
    record = marc_record_with_id(identifier="test001")
    record.add_field(
        Field(tag="245", subfields=[Subfield(code="a", value="Test Title")])
    )
    record.add_field(Field(tag="005", data="18530821094530.0"))
    return record


@when("I transform the MARC record", target_fixture="work")
def do_transform(marc_record: Record) -> InvisibleSourceWork:
    work = transform_record(marc_record)
    return work
