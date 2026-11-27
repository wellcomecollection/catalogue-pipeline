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

from adapters.steps.axiell_folio_sync.mapping.builders import _is_withheld
from adapters.steps.axiell_folio_sync.mapping.marc import MappingError, parse_xml
from adapters.steps.axiell_folio_sync.report import AxiellFolioSyncReport
from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from models.pipeline.source.work import DeletedSourceWork

from .test_harvest_untick import (
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


def _full_record(*, publish_to_web: str | None, for_deletion: bool) -> Record:
    """A record with enough for the Axiell transformer as well as the sync."""
    record = parse_xml(
        _record(flagged=True, publish_to_web=publish_to_web, for_deletion=for_deletion)
    )
    record.add_field(*parse_xml(_TRANSFORMER_FIELDS).fields)
    return record


@pytest.mark.parametrize("publish_to_web", ["yes", "no"])
@pytest.mark.parametrize("for_deletion", [True, False])
def test_withheld_agrees_with_the_axiell_transformer(
    publish_to_web: str, for_deletion: bool
) -> None:
    # A record the Axiell transformer suppresses must never be public in FOLIO.
    record = _full_record(publish_to_web=publish_to_web, for_deletion=for_deletion)
    work = AxiellWorkBuilder(
        record, last_modified=datetime(2020, 1, 1)
    ).transform_work()

    assert _is_withheld(record) == isinstance(work, DeletedSourceWork)
