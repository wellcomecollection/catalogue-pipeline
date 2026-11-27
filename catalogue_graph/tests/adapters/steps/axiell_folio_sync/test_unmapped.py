"""Selected records that fail to map.

A mapping error stops the write, so FOLIO would keep the last good sync, which
may no longer be true. The existing FOLIO records are hidden from discovery
until the record maps again. Decided on wellcomecollection/platform#6952.

These run against an in-memory FOLIO fake, not the sandbox.
"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

import pytest

from adapters.steps.axiell_folio_sync.mapping import unmapped_guid
from adapters.steps.axiell_folio_sync.mapping.marc import (
    MappingError,
    MissingFieldError,
)
from adapters.steps.axiell_folio_sync.report import AxiellFolioSyncReport

from .test_harvest_untick import (
    GUID,
    StatefulInventory,
    _assert_suppressed,
    _assert_unsuppressed,
    _record,
    _sync,
    _synced,
)
from .test_withheld import (
    FOR_DELETION,
    NO_MARKER,
    UNPUBLISHED,
    _assert_hidden_from_discovery_only,
)

PUBLISHED = _record(flagged=True)


_TITLE = "<datafield tag='245'><subfield code='a'>A Title</subfield></datafield>"
_CATEGORY = "<datafield tag='655'><subfield code='a'>Archives - Non-digital</subfield></datafield>"
_LOCATION = "<datafield tag='984'><subfield code='b'>215/HOME</subfield></datafield>"


def _without(content: str, field: str) -> str:
    assert field in content
    return content.replace(field, "")


def _unmapped(content: str) -> str:
    """The same record without its normal location, a mandatory field."""
    return _without(content, _LOCATION)


def _unknown_category(content: str) -> str:
    """The same record with a material type that is not in the mapping table."""
    assert "Archives - Non-digital" in content
    return content.replace("Archives - Non-digital", "Not A Category")


@pytest.mark.parametrize("field", [_TITLE, _CATEGORY, _LOCATION])
def test_a_missing_mandatory_field_hides_the_record(field: str) -> None:
    folio = _synced()

    resp = _sync(folio, _without(PUBLISHED, field))

    _assert_hidden_from_discovery_only(folio)
    assert resp.counts["unmapped_hidden"] == 1


def test_a_value_missing_from_a_mapping_table_hides_nothing() -> None:
    # It may be the table or the tenant at fault, which would hide every record.
    folio = _synced()
    puts_before = len(folio.puts)

    resp = _sync(folio, _unknown_category(PUBLISHED))

    _assert_unsuppressed(folio)
    assert len(folio.puts) == puts_before
    assert resp.counts["failed"] == 1
    assert resp.counts["unmapped_hidden"] == 0


@pytest.mark.parametrize("content", [UNPUBLISHED, FOR_DELETION])
def test_a_withheld_record_is_hidden_whatever_the_mapping_error(content: str) -> None:
    folio = _synced()

    resp = _sync(folio, _unknown_category(content))

    _assert_hidden_from_discovery_only(folio)
    assert resp.counts["unmapped_hidden"] == 1


@pytest.mark.parametrize("content", [PUBLISHED, UNPUBLISHED, FOR_DELETION])
def test_a_synced_record_that_fails_to_map_is_hidden(content: str) -> None:
    folio = _synced()

    resp = _sync(folio, _unmapped(content))

    _assert_hidden_from_discovery_only(folio)
    assert resp.counts["failed"] == 1
    assert resp.counts["unmapped_hidden"] == 1
    assert resp.counts["suppressed"] == 3
    assert resp.counts["withheld"] == 0


def test_a_record_that_maps_again_is_restored() -> None:
    folio = _synced()
    _sync(folio, _unmapped(PUBLISHED))

    resp = _sync(folio, PUBLISHED)

    _assert_unsuppressed(folio)
    assert resp.counts["updated"] == 3
    assert resp.counts["failed"] == 0


def test_hiding_an_unmapped_record_is_in_the_manifest(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    reports: list[AxiellFolioSyncReport] = []
    monkeypatch.setattr(
        AxiellFolioSyncReport, "publish", lambda self: reports.append(self)
    )

    _sync(_synced(), _unmapped(PUBLISHED))

    [entry] = reports[-1].deletions
    assert (entry.guid, entry.reason) == (GUID, "unmapped")
    assert [error.stage for error in reports[-1].errors] == ["mapping"]


def test_a_record_without_a_publish_marker_is_not_hidden() -> None:
    # A harvest that lost the marker must not hide every synced record.
    folio = _synced()
    puts_before = len(folio.puts)

    resp = _sync(folio, _unmapped(NO_MARKER))

    _assert_unsuppressed(folio)
    assert len(folio.puts) == puts_before
    assert resp.counts["failed"] == 1
    assert resp.counts["unmapped_hidden"] == 0


def test_reticking_an_unmapped_record_shows_it_to_staff() -> None:
    folio = _synced()
    _sync(folio, _record(flagged=False))
    _assert_suppressed(folio)

    _sync(folio, _unmapped(PUBLISHED))

    _assert_hidden_from_discovery_only(folio)


class _FailingItems(StatefulInventory):
    def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        if path.startswith("/inventory/items"):
            raise RuntimeError("item write failed")
        return super().put(path, payload)


def test_a_failed_item_write_does_not_stop_the_instance_being_hidden() -> None:
    folio = _FailingItems()
    _sync(folio, PUBLISHED)

    resp = _sync(folio, _unmapped(PUBLISHED))

    # The FOLIO transformer reads the instance's flag.
    assert folio.instance()["discoverySuppress"] is True
    assert folio.holdings()["discoverySuppress"] is True
    assert folio.item()["discoverySuppress"] is False
    assert resp.counts["suppressed"] == 2
    assert resp.counts["failed"] == 1


def test_an_unmapped_record_already_hidden_is_not_written_again() -> None:
    folio = _synced()
    _sync(folio, _unmapped(PUBLISHED))
    puts_before = len(folio.puts)

    resp = _sync(folio, _unmapped(PUBLISHED))

    assert len(folio.puts) == puts_before
    assert resp.counts["suppressed"] == 0
    assert resp.counts["unmapped_hidden"] == 0
    assert resp.counts["failed"] == 1


def test_an_unmapped_record_never_synced_creates_nothing() -> None:
    folio = StatefulInventory()

    resp = _sync(folio, _unmapped(PUBLISHED))

    assert folio.count() == 0
    assert resp.counts["failed"] == 1
    assert resp.counts["unmapped_hidden"] == 0


def test_an_unmapped_record_is_not_hidden_on_a_dry_run() -> None:
    folio = _synced()

    resp = _sync(folio, _unmapped(PUBLISHED), dry_run=True)

    _assert_unsuppressed(folio)
    # Planned, as an untick is on a dry run.
    assert resp.counts["unmapped_hidden"] == 1
    assert resp.counts["suppressed"] == 3


@pytest.mark.parametrize(
    "content",
    [
        NO_MARKER,
        _record(flagged=False),
        _record(flagged=True, level="Series"),
        _record(flagged=True, guid="not-a-guid"),
    ],
)
def test_unmapped_guid_is_none_for_records_that_must_not_be_hidden(
    content: str,
) -> None:
    assert unmapped_guid(content, MissingFieldError("missing")) is None


@pytest.mark.parametrize("content", [PUBLISHED, UNPUBLISHED, FOR_DELETION])
def test_unmapped_guid_is_the_001_when_a_field_is_missing(content: str) -> None:
    assert unmapped_guid(content, MissingFieldError("missing")) == GUID


def test_unmapped_guid_is_none_for_another_error_on_a_published_record() -> None:
    assert unmapped_guid(PUBLISHED, MappingError("unresolved")) is None


@pytest.mark.parametrize("content", [UNPUBLISHED, FOR_DELETION])
def test_unmapped_guid_is_the_001_of_a_withheld_record(content: str) -> None:
    assert unmapped_guid(content, MappingError("unresolved")) == GUID
