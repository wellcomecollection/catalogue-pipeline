"""Tests covering extraction of MARC 362 into a designation list.

https://www.loc.gov/marc/bibliographic/bd362.html
"""

from __future__ import annotations

import pytest
from pymarc.record import Field, Record, Subfield
from structlog.testing import capture_logs

from adapters.transformers.marc.designation import extract_designation


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="362",
                subfields=[
                    Subfield(code="a", value="Cyntaf"),
                    Subfield(code="a", value="Ail"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_repeated_a_subfield_logs_and_takes_the_first(marc_record: Record) -> None:
    """The Scala rejects the whole record; we keep the first ǂa and log."""
    with capture_logs() as logs:
        assert extract_designation(marc_record) == ["Cyntaf"]

    assert any(log["event"] == "Repeated non-repeating subfield $a" for log in logs)


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="362",
                subfields=[Subfield(code="a", value="TWKE-4")],
            ),
        )
    ],
    indirect=True,
)
def test_single_a_subfield_does_not_log(marc_record: Record) -> None:
    with capture_logs() as logs:
        assert extract_designation(marc_record) == ["TWKE-4"]

    assert logs == []
