import pytest
from pymarc.record import Field, Record, Subfield

from adapters.ebsco.transformers.ebsco_to_weco import transform_record
from models.pipeline.identifier import Identifiable


def test_no_subjects(marc_record: Record) -> None:
    assert transform_record(marc_record).data.subjects == []


@pytest.mark.parametrize(
    "marc_record",
    [
        pytest.param(
            [
                Field(
                    tag="610",
                    indicators=("", "2"),
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
    subjects = transform_record(marc_record).data.subjects
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
