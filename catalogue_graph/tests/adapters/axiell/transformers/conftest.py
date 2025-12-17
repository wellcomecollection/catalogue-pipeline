from __future__ import annotations
from models.pipeline.source.work import InvisibleSourceWork
from adapters.axiell.transformers.axiell_to_weco import transform_record
from pymarc.record import Field, Indicators, Record, Subfield
from pytest_bdd import parsers, then, when

from typing import Any

from tests.gherkin_steps.marc import *
from tests.gherkin_steps.work import *


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
