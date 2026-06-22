from __future__ import annotations

from datetime import datetime

from pymarc.record import Record
from pytest_bdd import given, when

from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from models.pipeline.source.work import VisibleSourceWork
from tests.adapters.transformers.axiell.conftest import make_axiell_record

# Allow * imports, pulling in individual step definitions is unwieldy
# ruff: noqa: F403, F405
from tests.gherkin_steps.marc import *
from tests.gherkin_steps.work import *


@given("a valid MARC record", target_fixture="marc_record")
def marc_record() -> Record:
    return make_axiell_record()


@when("I transform the MARC record", target_fixture="work")
def do_transform(marc_record: Record) -> VisibleSourceWork:
    return AxiellWorkBuilder(
        marc_record, last_modified=datetime(2020, 1, 1)
    ).transform_visible_work()
