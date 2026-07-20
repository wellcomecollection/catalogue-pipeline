import pytest

from adapters.steps.axiell_folio_sync.mapping import is_selected_for_sync


def _record(*, flag: str | None = None, record_type: str | None = None) -> str:
    """Minimal MARCXML with an optional 980 $a harvest flag and 351 $c record type."""
    fields = ""
    if flag is not None:
        fields += (
            f"<datafield tag='980'><subfield code='a'>{flag}</subfield></datafield>"
        )
    if record_type is not None:
        fields += f"<datafield tag='351'><subfield code='c'>{record_type}</subfield></datafield>"
    return f"<record>{fields}</record>"


def test_synced_when_harvest_flagged_and_item() -> None:
    assert is_selected_for_sync(_record(flag="Y", record_type="ITEM")) is True


def test_record_type_match_is_case_insensitive() -> None:
    assert is_selected_for_sync(_record(flag="anything", record_type="item")) is True


@pytest.mark.parametrize("record_type", ["Collection", "Series", "File", ""])
def test_skipped_when_not_item_level(record_type: str) -> None:
    assert is_selected_for_sync(_record(flag="Y", record_type=record_type)) is False


def test_skipped_when_no_harvest_flag() -> None:
    # 351 $c is ITEM but the 980 $a harvest flag is absent.
    assert is_selected_for_sync(_record(record_type="ITEM")) is False


def test_skipped_when_harvest_flag_empty() -> None:
    assert is_selected_for_sync(_record(flag="", record_type="ITEM")) is False


def test_skipped_when_record_type_absent() -> None:
    assert is_selected_for_sync(_record(flag="Y")) is False
