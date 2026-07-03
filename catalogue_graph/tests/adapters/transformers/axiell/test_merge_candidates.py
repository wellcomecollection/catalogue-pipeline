from datetime import datetime

from pymarc.record import Field, Record, Subfield

from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from models.pipeline.source.work import VisibleSourceWork
from tests.adapters.transformers.axiell.conftest import make_axiell_record

# mypy: allow-untyped-calls


def _visible(record: Record) -> VisibleSourceWork:
    result = AxiellWorkBuilder(
        record, last_modified=datetime(2020, 1, 1)
    ).transform_work()
    assert isinstance(result, VisibleSourceWork)
    return result


def test_calm_ref_no_yields_archivematica_merge_candidate() -> None:
    # make_axiell_record() already carries (Calm RefNo)TestRefNo
    candidates = _visible(make_axiell_record()).state.merge_candidates
    assert len(candidates) == 1
    assert candidates[0].reason == "Archivematica work"


def test_sierra_system_number_yields_calm_sierra_merge_candidate() -> None:
    record = make_axiell_record()
    record.add_field(
        Field(
            tag="035",
            subfields=[Subfield(code="a", value="(Bibliographic Number)b12345678")],
        )
    )
    reasons = {c.reason for c in _visible(record).state.merge_candidates}
    assert "CALM/Sierra harvest work" in reasons


def test_unrelated_identifier_produces_no_extra_merge_candidate() -> None:
    record = make_axiell_record()
    record.add_field(
        Field(
            tag="035",
            subfields=[Subfield(code="a", value="(AltRefNo)PP/WIT/A/1")],
        )
    )
    # Only the base calm-ref-no candidate; AltRefNo does not trigger one
    candidates = _visible(record).state.merge_candidates
    assert len(candidates) == 1
    assert candidates[0].reason == "Archivematica work"


def test_multiple_qualifying_identifiers_yield_multiple_merge_candidates() -> None:
    record = make_axiell_record()
    record.add_field(
        Field(
            tag="035",
            subfields=[Subfield(code="a", value="(Bibliographic Number)b12345678")],
        )
    )
    reasons = [c.reason for c in _visible(record).state.merge_candidates]
    assert "Archivematica work" in reasons
    assert "CALM/Sierra harvest work" in reasons
