from __future__ import annotations

from datetime import datetime

from pymarc.record import Record
from pytest_bdd import when

from models.pipeline.source.work import VisibleSourceWork
from tests.adapters.transformers.folio.folio_test_transformer import (
    FolioTransformerForTests,
)

# Allow * imports, pulling in individual step definitions is unwieldy
# ruff: noqa: F403, F405
from tests.gherkin_steps.marc import *
from tests.gherkin_steps.work import *


@when("I transform the MARC record", target_fixture="work")
def do_transform(marc_record: Record) -> VisibleSourceWork:
    transformer = FolioTransformerForTests()
    return transformer.transform_record(
        marc_record, source_modified_time=datetime(2020, 1, 1)
    )
