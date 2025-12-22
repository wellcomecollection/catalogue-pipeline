"""Tests covering extraction of subjects from MARC subject fields (e.g. 610).

Although the implementation currently lives under `adapters.ebsco.transformers.subjects`,
these tests are MARC-field level and can be shared across adapters.
"""

# mypy: allow-untyped-calls

from __future__ import annotations

from datetime import datetime

import pytest
from pymarc.record import Field, Indicators, Record, Subfield

from adapters.ebsco.transformers.subjects import extract_subjects
from models.pipeline.concept import Subject
from models.pipeline.identifier import Identifiable
from models.pipeline.work_data import WorkData
from tests.adapters.marc.marcxml_test_transformer import MarcXmlTransformerForTests


def _transform_subjects(marc_record: Record) -> list[Subject]:
    transformer = MarcXmlTransformerForTests(
        build_work_data=lambda r: WorkData(subjects=extract_subjects(r))
    )
    work = transformer.transform_record(marc_record, source_modified_time=datetime.now())
    return work.data.subjects


def test_no_subjects(marc_record: Record) -> None:
    assert _transform_subjects(marc_record) == []


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
    subjects = _transform_subjects(marc_record)
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
