from contextlib import suppress

import pytest
from pymarc.record import Field, Indicators, Record, Subfield

# mypy: allow-untyped-calls


@pytest.fixture
def marc_record(request: pytest.FixtureRequest) -> Record:
    record = Record()
    with suppress(AttributeError):
        record.add_field(*request.param)
    # Add mandatory fields if they are not mentioned in the params
    # This allows us to produce valid test data without having to
    # repeat these fields everywhere
    if record.get("001") is None:
        record.add_field(Field(tag="001", data="default_id"))
    if record.get("245") is None:
        record.add_field(
            Field(
                tag="245",
                indicators=Indicators("0", "1"),
                subfields=[Subfield(code="a", value="a title")],
            )
        )
    return record
