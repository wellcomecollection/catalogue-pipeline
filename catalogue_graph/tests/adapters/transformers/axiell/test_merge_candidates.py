from datetime import datetime

import pytest
from pymarc.record import Field, Record, Subfield

from adapters.extractors.oai_pmh.axiell import config as axiell_config
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


TEST_GUID = "0000abcd-0000-4000-8000-00000000ef01"


def _flagged_record(identifier: str = TEST_GUID) -> Record:
    record = make_axiell_record(identifier=identifier)
    record.add_field(Field(tag="980", subfields=[Subfield(code="a", value="x")]))
    return record


def _folio_candidates(record: Record) -> list[str]:
    return [
        c.id.source_identifier.value
        for c in _visible(record).state.merge_candidates
        if c.id.source_identifier.identifier_type.id == "folio-instance"
    ]


@pytest.fixture
def folio_merge_candidates(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(axiell_config, "AXIELL_FOLIO_MERGE_CANDIDATES", True)


@pytest.mark.usefixtures("folio_merge_candidates")
def test_flagged_item_yields_folio_sync_merge_candidate() -> None:
    candidates = _visible(_flagged_record()).state.merge_candidates
    folio = [c for c in candidates if c.reason == "Axiell/FOLIO sync work"]
    assert len(folio) == 1
    assert folio[0].id.source_identifier.identifier_type.id == "folio-instance"
    assert folio[0].id.source_identifier.value == TEST_GUID


@pytest.mark.usefixtures("folio_merge_candidates")
def test_folio_sync_merge_candidate_normalises_the_guid_like_the_sync() -> None:
    assert _folio_candidates(_flagged_record(f" {TEST_GUID.upper()} ")) == [TEST_GUID]


def test_no_folio_sync_merge_candidate_when_switched_off() -> None:
    assert _folio_candidates(_flagged_record()) == []


@pytest.mark.usefixtures("folio_merge_candidates")
def test_no_folio_sync_merge_candidate_without_harvest_flag() -> None:
    assert _folio_candidates(make_axiell_record(identifier=TEST_GUID)) == []


@pytest.mark.usefixtures("folio_merge_candidates")
def test_no_folio_sync_merge_candidate_above_item_level() -> None:
    record = _flagged_record()
    record["351"]["c"] = "Series"
    assert _folio_candidates(record) == []


@pytest.mark.usefixtures("folio_merge_candidates")
def test_no_folio_sync_merge_candidate_when_guid_is_not_a_uuid() -> None:
    assert _folio_candidates(_flagged_record("test001")) == []
