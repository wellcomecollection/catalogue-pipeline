"""Tests covering extraction of MARC 310 into a current frequency string.

https://www.loc.gov/marc/bibliographic/bd310.html

Although the extractor currently lives under the EBSCO adapter module, these
are MARC-field level tests and can be shared across adapters.
"""

# mypy: allow-untyped-calls

from __future__ import annotations

from datetime import datetime

import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.transformers.current_frequency import extract_current_frequency
from models.pipeline.work_data import WorkData
from tests.adapters.marc.marcxml_test_transformer import MarcXmlTransformerForTests


def _transform_current_frequency(marc_record: Record) -> str | None:
    transformer = MarcXmlTransformerForTests(
        build_work_data=lambda r: WorkData(
            current_frequency=extract_current_frequency(r)
        )
    )
    work = transformer.transform_record(
        marc_record, source_modified_time=datetime.now()
    )
    return work.data.current_frequency


def test_no_frequency(marc_record: Record) -> None:
    assert _transform_current_frequency(marc_record) is None


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="310",
                subfields=[Subfield(code="a", value="Samhain")],
            ),
        )
    ],
    indirect=True,
)
def test_frequency_a(marc_record: Record) -> None:
    assert _transform_current_frequency(marc_record) == "Samhain"


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="310",
                subfields=[Subfield(code="b", value="&lt;Sept. 1991-&gt;")],
            ),
        )
    ],
    indirect=True,
)
def test_frequency_b(marc_record: Record) -> None:
    assert _transform_current_frequency(marc_record) == "&lt;Sept. 1991-&gt;"


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="310",
                subfields=[
                    Subfield(code="a", value="Every lunar month"),
                    Subfield(code="b", value="Magáksicaagli Wí 1984"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_frequency_a_b(marc_record: Record) -> None:
    assert (
        _transform_current_frequency(marc_record)
        == "Every lunar month Magáksicaagli Wí 1984"
    )


@pytest.mark.parametrize(
    "marc_record",
    [
        (
            Field(
                tag="310",
                subfields=[
                    Subfield(code="a", value="Every lunar month"),
                    Subfield(code="b", value="Magáksicaagli Wí 1984 - 2002"),
                ],
            ),
            Field(
                tag="310",
                subfields=[
                    Subfield(code="a", value="Imbolc and Lammas"),
                    Subfield(code="b", value="1666 -"),
                ],
            ),
        )
    ],
    indirect=True,
)
def test_frequency_multiple(marc_record: Record) -> None:
    assert (
        _transform_current_frequency(marc_record)
        == "Every lunar month Magáksicaagli Wí 1984 - 2002 Imbolc and Lammas 1666 -"
    )
