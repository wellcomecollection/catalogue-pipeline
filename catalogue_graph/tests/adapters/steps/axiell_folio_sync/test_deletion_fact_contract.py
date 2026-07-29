"""Contract tests for consuming deletion facts in the FOLIO sync (platform#6440).

These pin the two invariants the future deletes implementation relies on: a
fact's guid maps directly onto the sync's FOLIO hrids, and the guid derivation
used by the adapter-side reconcile step stays byte-identical to the sync's
source_id derivation.
"""

from datetime import UTC, datetime

from adapters.steps.axiell_folio_sync.mapper import extract, parse_xml
from adapters.steps.axiell_folio_sync.mapping import (
    _holdings_hrid,
    _instance_hrid,
    _item_hrid,
)
from adapters.transformers.builders.axiell_work_builder import AxiellWorkBuilder
from adapters.utils.axiell_changeset_reader import SupersededGuid
from utils.marc import parse_single_marc_record

# 001 padded with whitespace on purpose: both derivations must strip it.
MARCXML = (
    "<record><leader>00000nam a2200000   4500</leader>"
    "<controlfield tag='005'>20251225123045.0</controlfield>"
    "<controlfield tag='001'>  guid-001  </controlfield>"
    "<datafield tag='245' ind1='0' ind2='0'>"
    "<subfield code='a'>A title</subfield></datafield>"
    "</record>"
)


def test_superseded_guid_maps_directly_onto_folio_hrids() -> None:
    """A deletion fact's guid is the FOLIO source_id, so suppression targets
    derive with no adapter-row read."""
    deletion = SupersededGuid(
        fact_id="collect-1/cs-1",
        record_id="collect-1",
        guid="guid-001",
        changeset_id="cs-1",
        last_modified=datetime(2026, 7, 1, tzinfo=UTC),
    )
    assert _instance_hrid(deletion.guid) == "AxC-instance-guid-001"
    assert _holdings_hrid(deletion.guid) == "AxC-holding-guid-001"
    assert _item_hrid(deletion.guid) == "AxC-item-guid-001"


def test_fact_guid_and_folio_source_id_derivations_agree() -> None:
    """The reconcile step's guid (via AxiellWorkBuilder / pymarc) and the
    sync's source_id (via mapper.extract) must stay byte-identical, or facts
    stop mapping onto FOLIO hrids."""
    work_builder_guid = AxiellWorkBuilder(
        parse_single_marc_record(MARCXML), datetime(2026, 7, 1, tzinfo=UTC)
    ).source_identifier.value
    sync_source_id = extract(parse_xml(MARCXML), "001")

    assert work_builder_guid == "guid-001"
    assert sync_source_id == work_builder_guid
