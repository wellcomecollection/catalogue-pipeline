from __future__ import annotations
from models.pipeline.source.work import InvisibleSourceWork
from adapters.axiell.transformers.axiell_to_weco import transform_record
from pymarc.record import Field, Indicators, Record, Subfield
from pytest_bdd import parsers, then, when

from typing import Any

from tests.adapters.marc.steps.givens import *
from tests.adapters.marc.steps.thens import *


@when("I transform the MARC record", target_fixture="work")
def do_transform(marc_record: Record) -> InvisibleSourceWork:
    work = transform_record(marc_record)
    return work


@then(parsers.parse("the work's source modified time is {date_str}"))
def work_last_modified_date(
        work: InvisibleSourceWork, date_str: str
) -> None:
    assert work.state.source_modified_time == date_str
