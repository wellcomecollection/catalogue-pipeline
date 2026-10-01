import pytest
from pymarc.record import Field, Indicators, Record, Subfield

from adapters.transformers.marc.production import extract_production

FIELD_260 = Field(
    tag="260",
    subfields=[Subfield(code="a", value="Paris :"), Subfield(code="c", value="1955")],
)
FIELD_264 = Field(
    tag="264",
    indicators=Indicators(" ", "1"),
    subfields=[Subfield(code="a", value="London :"), Subfield(code="c", value="1999")],
)


@pytest.mark.parametrize("marc_record", [(FIELD_260, FIELD_264)], indirect=True)
def test_prefers_260_by_default(marc_record: Record) -> None:
    (production,) = extract_production(marc_record)
    assert production.label == "Paris : 1955"
    assert production.function is None


@pytest.mark.parametrize("marc_record", [(FIELD_260, FIELD_264)], indirect=True)
def test_prefers_264_when_asked(marc_record: Record) -> None:
    (production,) = extract_production(marc_record, prefer="264")
    assert production.label == "London : 1999"
    assert production.function is not None
    assert production.function.label == "Publication"


@pytest.mark.parametrize("marc_record", [(FIELD_260,)], indirect=True)
def test_preference_is_irrelevant_with_one_field(marc_record: Record) -> None:
    assert extract_production(marc_record, prefer="264") == extract_production(
        marc_record
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(tag="008", data="790922s1757    enk||||      o00||||eng ccam   "),
            Field(
                tag="264",
                indicators=Indicators(" ", "1"),
                subfields=[Subfield(code="c", value="[date not identified]")],
            ),
        )
    ],
    indirect=True,
)
def test_unparseable_date_keeps_its_label_with_the_008_range(
    marc_record: Record,
) -> None:
    (production,) = extract_production(marc_record)
    (period,) = production.dates
    assert period.label == "[date not identified]"
    assert period.range is not None
    assert period.range.from_time == "1757-01-01T00:00:00Z"
    assert period.range.label == "1757"


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="264",
                indicators=Indicators(" ", "x"),
                subfields=[Subfield(code="a", value="London")],
            ),
        )
    ],
    indirect=True,
)
def test_unrecognised_264_indicator_skips_the_field(marc_record: Record) -> None:
    assert extract_production(marc_record) == []
