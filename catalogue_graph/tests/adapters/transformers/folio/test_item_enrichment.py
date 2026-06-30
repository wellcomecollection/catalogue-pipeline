"""Tests for FOLIO item enrichment at transform time (RFC 088 / Option C).

Covers both the unit level (FolioWorkBuilder emitting items from enrichment
content) and the end-to-end join (the transformer handler joining the items store
onto the bib store and producing works with `folio-item` item identifiers).
"""

from datetime import UTC, datetime
from uuid import uuid1

import pytest
from pyiceberg.table import Table as IcebergTable

import adapters.extractors.oai_pmh.folio.config as adapter_config
from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedInstance,
    FolioEnrichedItem,
)
from adapters.extractors.oai_pmh.folio.runtime import FOLIO_CONFIG
from adapters.steps.transformer import TransformerEvent, handler
from adapters.transformers.builders.folio_work_builder import FolioWorkBuilder
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg import LocalIcebergTableConfig, get_local_table
from models.pipeline.identifier import Identifiable
from tests.adapters.conftest import adapter_records_to_table
from tests.adapters.extractors.ebsco.helpers import prepare_changeset
from tests.mocks import MockElasticsearchClient
from utils.marc import parse_single_marc_record

FOLIO_NAMESPACE = FOLIO_CONFIG.config.adapter_namespace
ITEMS_NAMESPACE = "folio-items"

BIB_RECORD = (
    "<record><leader>00000nam a2200000   4500</leader>"
    "<controlfield tag='005'>20261225123045.0</controlfield>"
    "<controlfield tag='001'>{id}</controlfield>"
    "<datafield tag='245' ind1='0' ind2='0'>"
    "<subfield code='a'>Folio With Items</subfield></datafield></record>"
)


# ---------------------------------------------------------------------------
# Unit: FolioWorkBuilder builds items from joined enrichment content
# ---------------------------------------------------------------------------
def test_work_builder_emits_items_with_folio_item_identifier() -> None:
    enriched = FolioEnrichedInstance(
        instance_id="inst-1",
        items=[
            FolioEnrichedItem(id="item-uuid-1", copy_number="1"),
            FolioEnrichedItem(id="item-uuid-2"),
        ],
    )
    record = parse_single_marc_record(BIB_RECORD.format(id="inst-1"))
    builder = FolioWorkBuilder(
        record,
        datetime.now(UTC),
        enrichment_content=enriched.to_store_content(),
    )

    items = builder.items
    assert len(items) == 2

    first = items[0]
    assert isinstance(first.id, Identifiable)
    assert first.id.source_identifier.identifier_type.id == "folio-item"
    assert first.id.source_identifier.ontology_type == "Item"
    assert first.id.source_identifier.value == "item-uuid-1"
    assert first.title == "1"


def test_work_builder_emits_no_items_without_enrichment() -> None:
    record = parse_single_marc_record(BIB_RECORD.format(id="inst-1"))
    builder = FolioWorkBuilder(record, datetime.now(UTC))
    assert builder.items == []


# ---------------------------------------------------------------------------
# End-to-end: the transformer joins the items store onto bib records
# ---------------------------------------------------------------------------
def _make_items_store(records: dict[str, FolioEnrichedInstance]) -> AdapterStore:
    config = LocalIcebergTableConfig(
        table_name=str(uuid1()),
        namespace="test",
        db_name="test_catalog",
    )
    table = get_local_table(config)
    store = AdapterStore(table, ITEMS_NAMESPACE)
    if records:
        rows = adapter_records_to_table(
            [
                {"id": instance_id, "content": instance.to_store_content()}
                for instance_id, instance in records.items()
            ],
            namespace=ITEMS_NAMESPACE,
        )
        store.incremental_update(rows)
    return store


def _item_identifiers(source: dict) -> list[tuple[str, str]]:
    """Return (identifier_type_id, value) for each item on a work _source."""
    return [
        (
            item["id"]["sourceIdentifier"]["identifierType"]["id"],
            item["id"]["sourceIdentifier"]["value"],
        )
        for item in source["data"]["items"]
    ]


def test_transformer_attaches_enriched_items(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(adapter_config, "PIPELINE_DATE", "dev")
    monkeypatch.setattr(adapter_config, "INDEX_DATE", "2026-01-01")

    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"inst-1": BIB_RECORD.format(id="inst-1")},
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    items_store = _make_items_store(
        {
            "inst-1": FolioEnrichedInstance(
                instance_id="inst-1",
                items=[
                    FolioEnrichedItem(id="item-uuid-1"),
                    FolioEnrichedItem(id="item-uuid-2"),
                ],
            )
        }
    )
    monkeypatch.setattr(
        "adapters.steps.transformer.build_items_store",
        lambda **kwargs: items_store,
    )

    MockElasticsearchClient.inputs.clear()
    result = handler(
        event=TransformerEvent(
            transformer_type="folio",
            job_id="20260101T1200",
            changeset_ids=[changeset_id],
        ),
        es_mode="local",
        use_rest_api_table=False,
    )

    assert result.failures is None
    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    source = by_id["Work[folio-instance/inst-1]"]["_source"]

    assert _item_identifiers(source) == [
        ("folio-item", "item-uuid-1"),
        ("folio-item", "item-uuid-2"),
    ]


def test_transformer_emits_no_items_when_instance_not_enriched(
    temporary_table: IcebergTable, monkeypatch: pytest.MonkeyPatch
) -> None:
    """A bib with no matching items-store row still transforms, with no items."""
    monkeypatch.setattr(adapter_config, "PIPELINE_DATE", "dev")
    monkeypatch.setattr(adapter_config, "INDEX_DATE", "2026-01-01")

    changeset_id = prepare_changeset(
        temporary_table,
        monkeypatch,
        {"inst-2": BIB_RECORD.format(id="inst-2")},
        namespace=FOLIO_NAMESPACE,
        transformer_type="folio",
    )

    items_store = _make_items_store({})  # empty items store
    monkeypatch.setattr(
        "adapters.steps.transformer.build_items_store",
        lambda **kwargs: items_store,
    )

    MockElasticsearchClient.inputs.clear()
    result = handler(
        event=TransformerEvent(
            transformer_type="folio",
            job_id="20260101T1200",
            changeset_ids=[changeset_id],
        ),
        es_mode="local",
        use_rest_api_table=False,
    )

    assert result.failures is None
    by_id = {op["_id"]: op for op in MockElasticsearchClient.inputs}
    source = by_id["Work[folio-instance/inst-2]"]["_source"]
    assert source["data"]["items"] == []
