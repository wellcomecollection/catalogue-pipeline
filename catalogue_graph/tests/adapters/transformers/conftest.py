from contextlib import suppress

import pytest
from pymarc.record import Field, Indicators, Record, Subfield

# mypy: allow-untyped-calls


def _record_with_fields(*fields: Field) -> Record:
    record = Record()
    record.add_field(*fields)

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


@pytest.fixture
def marc_record(request: pytest.FixtureRequest) -> Record:
    """Create a MARC record with sensible defaults for transformer unit tests.

    Tests can pass one or more pymarc Field objects via indirect parametrisation:
        @pytest.mark.parametrize("marc_record", [(Field(...),)], indirect=True)

    We also ensure mandatory fields exist unless explicitly supplied.
    """

    fields = []
    with suppress(AttributeError):
        # request.param will typically be a tuple of Field objects
        fields = request.param

    return _record_with_fields(*fields)


def _907_field(value: str) -> Field:
    return Field(
        tag="907",
        indicators=Indicators(" ", " "),
        subfields=[Subfield(code="a", value=value)],
    )
