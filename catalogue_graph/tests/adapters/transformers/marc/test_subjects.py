"""Tests covering extraction of subjects from MARC subject fields (e.g. 610).

Although the implementation currently lives under `adapters.transformers.ebsco.subjects`,
these tests are MARC-field level and can be shared across adapters.
"""

from __future__ import annotations

import pytest
from pymarc.record import Field, Indicators, Record, Subfield

from adapters.transformers.ebsco.subjects import extract_subjects
from models.pipeline.identifier import Identifiable


def test_no_subjects(marc_record: Record) -> None:
    assert extract_subjects(marc_record) == []


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="610",
                    indicators=Indicators("", "2"),
                    subfields=[
                        Subfield(code="a", value="A"),
                        Subfield(code="b", value="B"),
                        Subfield(code="c", value="C"),
                        Subfield(code="d", value="D"),
                        Subfield(code="e", value="E"),
                        Subfield(code="t", value="T"),
                        Subfield(code="p", value="P"),
                        Subfield(code="q", value="Q"),
                        Subfield(code="l", value="L"),
                    ],
                )
            ],
            id="single-subject",
        )
    ],
    indirect=["marc_record"],
)
def test_single_subject(marc_record: Record) -> None:
    subjects = extract_subjects(marc_record)
    assert len(subjects) == 1

    subject = subjects[0]
    assert subject.label == "A B C D E"
    assert isinstance(subject.id, Identifiable)
    assert subject.id.source_identifier.value == "a b c d e"
    assert subject.type == "Subject"

    assert subject.concepts[0].label == "A B"
    assert isinstance(subject.concepts[0].id, Identifiable)
    assert subject.concepts[0].id.source_identifier.value == "a b"
    assert subject.concepts[0].type == "Organisation"
