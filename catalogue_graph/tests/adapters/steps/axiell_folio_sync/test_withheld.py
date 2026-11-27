"""Selected records that Collections withholds from the public.

A record with Harvest ticked that is not published to the web (MARC 981 $a), or
is ticked for deletion (MARC 985 $a), is synced hidden from discovery and left
visible to staff. Decided on wellcomecollection/platform#6941.

These run against an in-memory FOLIO fake, not the sandbox.
"""

from __future__ import annotations

from datetime import datetime

import pytest
from pymarc.record import Record

from adapters.steps.axiell_folio_sync.mapping import withheld_guid
from adapters.steps.axiell_folio_sync.mapping.builders import _is_withheld
from adapters.steps.axiell_folio_sync.mapping.marc import MappingError, parse_xml
from adapters.steps.axiell_folio_sync.report import AxiellFolioSyncReport
from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from models.pipeline.source.work import DeletedSourceWork

from .test_harvest_untick import (
    GUID,
    StatefulInventory,
    _assert_suppressed,
    _assert_unsuppressed,
    _record,
    _sync,
    _synced,
)

# mypy: allow-untyped-calls

UNPUBLISHED = _record(flagged=True, publish_to_web="no")
FOR_DELETION = _record(flagged=True, for_deletion=True)
NO_MARKER = _record(flagged=True, publish_to_web=None)


def _assert_hidden_from_discovery_only(folio: StatefulInventory) -> None:
    assert folio.instance()["discoverySuppress"] is True
    assert folio.holdings()["discoverySuppress"] is True
    assert folio.item()["discoverySuppress"] is True
    # Staff still need to find and work with the record.
    assert folio.instance()["staffSuppress"] is False


@pytest.mark.parametrize("content", [UNPUBLISHED, FOR_DELETION])
def test_a_withheld_record_is_created_hidden_from_discovery(content: str) -> None:
    folio = StatefulInventory()

    resp = _sync(folio, content)

    _assert_hidden_from_discovery_only(folio)
    assert resp.counts["created"] == 3
    assert resp.counts["withheld"] == 1
    assert resp.total_errors == 0


@pytest.mark.parametrize("content", [UNPUBLISHED, FOR_DELETION])
def test_withholding_a_synced_record_hides_it(content: str) -> None:
    folio = _synced()
    _assert_unsuppressed(folio)

    resp = _sync(folio, content)

    _assert_hidden_from_discovery_only(folio)
    assert resp.counts["updated"] == 3
    assert resp.counts["created"] == 0
    # Not a suppression in the report's sense: that count is for unticked
    # Harvest and the second pass.
    assert resp.counts["suppressed"] == 0


def test_publishing_a_withheld_record_restores_it() -> None:
    folio = StatefulInventory()
    _sync(folio, UNPUBLISHED)

    resp = _sync(folio, _record(flagged=True))

    _assert_unsuppressed(folio)
    assert resp.counts["updated"] == 3
    assert resp.counts["withheld"] == 0


def test_a_deletion_tick_withholds_a_published_record() -> None:
    assert _is_withheld(parse_xml(_record(flagged=True, for_deletion=True))) is True


def test_reticking_a_withheld_record_shows_it_to_staff_only() -> None:
    folio = _synced()
    _sync(folio, _record(flagged=False))
    _assert_suppressed(folio)

    _sync(folio, UNPUBLISHED)

    _assert_hidden_from_discovery_only(folio)


def test_an_unticked_withheld_record_is_suppressed_like_any_unticked_one() -> None:
    folio = _synced()

    resp = _sync(folio, _record(flagged=False, publish_to_web="no"))

    _assert_suppressed(folio)
    assert resp.counts["unflagged"] == 1
    assert resp.counts["withheld"] == 0


@pytest.mark.parametrize("publish_to_web", [None, "", "maybe"])
def test_a_record_without_a_publish_marker_is_an_error_and_not_written(
    publish_to_web: str | None,
) -> None:
    # A harvest that lost the marker must not hide every synced record.
    folio = _synced()
    puts_before = len(folio.puts)

    resp = _sync(folio, _record(flagged=True, publish_to_web=publish_to_web))

    _assert_unsuppressed(folio)
    assert len(folio.puts) == puts_before
    assert resp.counts["failed"] == 1
    assert resp.counts["withheld"] == 0


def test_a_new_record_without_a_publish_marker_is_not_created() -> None:
    folio = StatefulInventory()

    _sync(folio, NO_MARKER)

    assert folio.count() == 0


def test_a_deletion_tick_withholds_without_a_publish_marker() -> None:
    record = parse_xml(_record(flagged=True, publish_to_web=None, for_deletion=True))
    assert _is_withheld(record) is True


def test_missing_publish_marker_raises_a_mapping_error() -> None:
    with pytest.raises(MappingError, match="981"):
        _is_withheld(parse_xml(NO_MARKER))


def test_withheld_records_are_flagged_in_the_manifest(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    reports: list[AxiellFolioSyncReport] = []
    monkeypatch.setattr(
        AxiellFolioSyncReport, "publish", lambda self: reports.append(self)
    )

    _sync(StatefulInventory(), UNPUBLISHED)
    _sync(StatefulInventory(), _record(flagged=True))

    assert [entry.withheld for r in reports for entry in r.successful] == [True, False]


_TRANSFORMER_FIELDS = (
    "<record>"
    "<controlfield tag='005'>20200101000000.0</controlfield>"
    "<datafield tag='035'><subfield code='a'>(Calm RefNo)TEST/1</subfield></datafield>"
    "<datafield tag='583' ind1='0'><subfield code='l'>catalogued</subfield></datafield>"
    "</record>"
)


def _full_record(
    *,
    publish_to_web: str | None = "yes",
    for_deletion: bool = False,
    alt_ref_no: str = "TEST/1",
) -> Record:
    """A record with enough for the Axiell transformer as well as the sync."""
    record = parse_xml(
        _record(flagged=True, publish_to_web=publish_to_web, for_deletion=for_deletion)
    )
    record.add_field(*parse_xml(_TRANSFORMER_FIELDS).fields)
    record.add_field(
        *parse_xml(
            "<record><datafield tag='035'>"
            f"<subfield code='a'>(AltRefNo){alt_ref_no}</subfield>"
            "</datafield></record>"
        ).fields
    )
    return record


def _is_suppressed_by_transformer(record: Record) -> bool:
    work = AxiellWorkBuilder(
        record, last_modified=datetime(2020, 1, 1)
    ).transform_work()
    return isinstance(work, DeletedSourceWork)


@pytest.mark.parametrize("publish_to_web", ["yes", "no"])
@pytest.mark.parametrize("for_deletion", [True, False])
def test_withheld_agrees_with_the_axiell_transformer(
    publish_to_web: str, for_deletion: bool
) -> None:
    # A record the Axiell transformer suppresses must never be public in FOLIO.
    record = _full_record(publish_to_web=publish_to_web, for_deletion=for_deletion)

    assert _is_withheld(record) == _is_suppressed_by_transformer(record)


def test_a_missing_marker_is_an_error_here_and_a_suppression_in_the_transformer() -> (
    None
):
    record = _full_record(publish_to_web=None)

    assert _is_suppressed_by_transformer(record) is True
    with pytest.raises(MappingError):
        _is_withheld(record)


def test_resource_guides_are_suppressed_by_the_transformer_only() -> None:
    # The sync does not check for AMSG; recorded so the difference is deliberate.
    record = _full_record(alt_ref_no="AMSG/1")

    assert _is_suppressed_by_transformer(record) is True
    assert _is_withheld(record) is False


# -- a withheld record that fails to map --


def _unmapped(content: str) -> str:
    """The same record without its normal location, which the mapping requires."""
    location = "<datafield tag='984'><subfield code='b'>215/HOME</subfield></datafield>"
    assert location in content
    return content.replace(location, "")


@pytest.mark.parametrize("content", [UNPUBLISHED, FOR_DELETION])
def test_a_withheld_record_that_fails_to_map_is_still_hidden(content: str) -> None:
    folio = _synced()

    resp = _sync(folio, _unmapped(content))

    _assert_hidden_from_discovery_only(folio)
    assert resp.counts["failed"] == 1
    assert resp.counts["withheld_unmapped"] == 1
    assert resp.counts["suppressed"] == 3
    assert resp.counts["withheld"] == 0


def test_hiding_an_unmapped_withheld_record_is_in_the_manifest(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    reports: list[AxiellFolioSyncReport] = []
    monkeypatch.setattr(
        AxiellFolioSyncReport, "publish", lambda self: reports.append(self)
    )

    _sync(_synced(), _unmapped(UNPUBLISHED))

    [entry] = reports[-1].deletions
    assert (entry.guid, entry.reason) == (GUID, "withheld")
    assert [error.stage for error in reports[-1].errors] == ["mapping"]


def test_an_unmapped_withheld_record_already_hidden_is_not_written_again() -> None:
    folio = _synced()
    _sync(folio, UNPUBLISHED)
    puts_before = len(folio.puts)

    resp = _sync(folio, _unmapped(UNPUBLISHED))

    assert len(folio.puts) == puts_before
    assert resp.counts["suppressed"] == 0


def test_an_unmapped_withheld_record_never_synced_creates_nothing() -> None:
    folio = StatefulInventory()

    resp = _sync(folio, _unmapped(UNPUBLISHED))

    assert folio.count() == 0
    assert resp.counts["failed"] == 1


def test_an_unmapped_withheld_record_is_not_hidden_on_a_dry_run() -> None:
    folio = _synced()

    _sync(folio, _unmapped(UNPUBLISHED), dry_run=True)

    _assert_unsuppressed(folio)


def test_a_published_record_that_fails_to_map_is_left_alone() -> None:
    folio = _synced()
    puts_before = len(folio.puts)

    resp = _sync(folio, _unmapped(_record(flagged=True)))

    _assert_unsuppressed(folio)
    assert len(folio.puts) == puts_before
    assert resp.counts["withheld_unmapped"] == 0


@pytest.mark.parametrize(
    "content",
    [
        _record(flagged=True),
        NO_MARKER,
        _record(flagged=False, publish_to_web="no"),
        _record(flagged=True, publish_to_web="no", level="Series"),
        _record(flagged=True, publish_to_web="no", guid="not-a-guid"),
    ],
)
def test_withheld_guid_is_none_unless_selected_and_withheld(content: str) -> None:
    assert withheld_guid(content) is None


def test_withheld_guid_is_the_001_of_a_withheld_record() -> None:
    assert withheld_guid(UNPUBLISHED) == GUID
