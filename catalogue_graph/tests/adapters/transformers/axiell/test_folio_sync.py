import pytest
from pymarc.record import Field, Record, Subfield

from adapters.extractors.oai_pmh.axiell import config as axiell_config
from adapters.steps.axiell_folio_sync.mapping import _instance_id
from adapters.steps.axiell_folio_sync.mapping.builders import _selection
from adapters.transformers.axiell.folio_sync import extract_synced_folio_instance_id

# mypy: allow-untyped-calls

TEST_GUID = "00000000-0000-4000-8000-000000000001"


def _record(source_id: str, record_type: str | None, flag: str | None) -> Record:
    record = Record()
    record.add_field(Field(tag="001", data=source_id))
    if record_type is not None:
        record.add_field(
            Field(tag="351", subfields=[Subfield(code="c", value=record_type)])
        )
    if flag is not None:
        record.add_field(Field(tag="980", subfields=[Subfield(code="a", value=flag)]))
    return record


def _synced_instance_id(record: Record) -> str | None:
    """What the sync itself would write, from its own selection and id functions."""
    if _selection(record) != "selected":
        return None
    try:
        return _instance_id(record["001"].data or "")
    except ValueError:
        return None


@pytest.mark.parametrize("source_id", [TEST_GUID, f" {TEST_GUID.upper()} ", "test001"])
@pytest.mark.parametrize("record_type", ["ITEM", "item", " Item ", "Series", "", None])
@pytest.mark.parametrize("flag", ["x", "Y", "", "   ", None])
def test_agrees_with_the_sync_on_which_records_have_a_folio_instance(
    source_id: str, record_type: str | None, flag: str | None
) -> None:
    # The rule is copied from the sync, so a change there has to fail here.
    record = _record(source_id, record_type, flag)
    assert extract_synced_folio_instance_id(record) == _synced_instance_id(record)


@pytest.mark.parametrize(
    "value, expected", [("true", True), ("True", True), (" FALSE ", False)]
)
def test_flag_parsing_ignores_case_and_whitespace(
    monkeypatch: pytest.MonkeyPatch, value: str, expected: bool
) -> None:
    monkeypatch.setenv("TEST_FLAG", value)
    assert axiell_config._parse_flag("TEST_FLAG", "false") is expected


def test_flag_parsing_rejects_anything_else(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("TEST_FLAG", "1")
    with pytest.raises(ValueError):
        axiell_config._parse_flag("TEST_FLAG", "false")
