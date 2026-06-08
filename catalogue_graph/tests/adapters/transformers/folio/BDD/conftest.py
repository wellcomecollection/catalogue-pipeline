from __future__ import annotations

from datetime import datetime

import pytest
from pymarc.record import Record
from pytest_bdd import then, when

from adapters.transformers.folio_record_transformer import FolioRecordTransformer
from models.pipeline.source.work import VisibleSourceWork

# Allow * imports, pulling in individual step definitions is unwieldy
# ruff: noqa: F403, F405
from tests.gherkin_steps.marc import *
from tests.gherkin_steps.work import *


@when("I transform the MARC record", target_fixture="work")
def do_transform(marc_record: Record) -> VisibleSourceWork:
    return FolioRecordTransformer(
        marc_record, last_modified=datetime(2020, 1, 1)
    ).visible_work


@then("transforming the record raises ValueError")
def check_transform_raises_error(marc_record: Record) -> None:
    with pytest.raises(ValueError):
        _ = FolioRecordTransformer(
            marc_record, last_modified=datetime(2020, 1, 1)
        ).visible_work
