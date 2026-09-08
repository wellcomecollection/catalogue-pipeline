from __future__ import annotations

import re
from datetime import datetime

import pytest
from pymarc.record import Record
from pytest_bdd import parsers, then, when

from adapters.transformers.builders.folio_work_builder import FolioWorkBuilder
from models.pipeline.source.work import VisibleSourceWork

# Allow * imports, pulling in individual step definitions is unwieldy
# ruff: noqa: F403, F405
from tests.gherkin_steps.marc import *
from tests.gherkin_steps.work import *


@when("I transform the MARC record", target_fixture="work")
def do_transform(marc_record: Record) -> VisibleSourceWork:
    return FolioWorkBuilder(
        marc_record, last_modified=datetime(2020, 1, 1)
    ).transform_visible_work()


@then(parsers.parse('transforming the record raises ValueError "{message}"'))
def check_transform_raises_error(marc_record: Record, message: str) -> None:
    with pytest.raises(ValueError, match=re.escape(message)):
        _ = FolioWorkBuilder(
            marc_record, last_modified=datetime(2020, 1, 1)
        ).transform_visible_work()
