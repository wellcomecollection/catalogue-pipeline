from contextlib import suppress

import pytest
from pymarc.record import Field, Indicators, Record, Subfield

# mypy: allow-untyped-calls


@pytest.fixture
def marc_record(request: pytest.FixtureRequest) -> Record:
    """Create a MARC record with sensible defaults for transformer unit tests.

    Tests can pass one or more pymarc Field objects via indirect parametrisation:
        @pytest.mark.parametrize("marc_record", [(Field(...),)], indirect=True)

    We also ensure mandatory fields exist unless explicitly supplied.
    """

    record = Record()

    with suppress(AttributeError):
        # request.param will typically be a tuple of Field objects
        record.add_field(*request.param)

    # Add mandatory fields if they are not mentioned in the params.
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
