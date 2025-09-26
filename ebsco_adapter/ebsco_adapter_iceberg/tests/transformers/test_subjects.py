import pytest
from pymarc.record import Field, Record, Subfield

from models.work import ConceptType, SourceConcept, SourceIdentifier
from transformers.ebsco_to_weco import transform_record

from ..helpers import lone_element


def test_no_subjects(marc_record: Record) -> None:
    assert transform_record(marc_record).subjects == []
