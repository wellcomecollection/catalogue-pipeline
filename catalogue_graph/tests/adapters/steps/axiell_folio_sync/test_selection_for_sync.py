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


def test_skipped_when_record_type_absent() -> None:
    assert is_selected_for_sync(_record(flag="Y")) is False


def test_skipped_when_harvest_flag_absent() -> None:
    """The flag is the opt-in: an item-level record that carries no 980 $a is not
    synced, however complete the rest of it is."""
    assert is_selected_for_sync(_record(record_type="ITEM")) is False


def test_skipped_when_harvest_flag_is_empty() -> None:
    """An empty 980 $a is not an opt-in. The XSLT emits the field only when the
    flag is set, but a whitespace-only value must not slip through either."""
    assert is_selected_for_sync(_record(flag="", record_type="ITEM")) is False
    assert is_selected_for_sync(_record(flag="   ", record_type="ITEM")) is False


def test_both_gates_have_to_pass() -> None:
    """Neither gate alone is enough."""
    assert is_selected_for_sync(_record(flag="Y", record_type="ITEM")) is True
    assert is_selected_for_sync(_record(flag="Y", record_type="Series")) is False
    assert is_selected_for_sync(_record(record_type="ITEM")) is False
    assert is_selected_for_sync(_record(flag="Y")) is False


def test_select_and_build_applies_the_same_gates() -> None:
    """is_selected_for_sync and select_and_build must never disagree, so both
    call the same helper. An unflagged record builds nothing."""
    from adapters.steps.axiell_folio_sync.mapping import select_and_build

    unflagged = (
        "<record>"
        "<controlfield tag='001'>guid-1</controlfield>"
        "<datafield tag='351'><subfield code='c'>ITEM</subfield></datafield>"
        "<datafield tag='245'><subfield code='a'>A Title</subfield></datafield>"
        "<datafield tag='984'><subfield code='b'>215/HOME</subfield></datafield>"
        "</record>"
    )
    assert select_and_build(unflagged, None) is None  # type: ignore[arg-type]
