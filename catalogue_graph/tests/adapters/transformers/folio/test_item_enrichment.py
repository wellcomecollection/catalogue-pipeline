"""Tests for FOLIO item enrichment at transform time.

Covers both the unit level (FolioWorkBuilder emitting items from enrichment
content) and the end-to-end join (the transformer handler joining the items store
onto the bib store and producing works with `folio-item` item identifiers).
"""

from datetime import UTC, datetime
from uuid import uuid1

import pytest
from pyiceberg.expressions import In
from pyiceberg.table import Table as IcebergTable

import adapters.extractors.oai_pmh.folio.config as adapter_config
from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedInstance,
    FolioEnrichedItem,
)
from adapters.extractors.oai_pmh.folio.runtime import FOLIO_CONFIG
from adapters.steps.transformer import TransformerEvent, handler
from adapters.transformers import adapter_store_source
from adapters.transformers.adapter_store_source import AdapterStoreSource
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
            FolioEnrichedItem(id="item-uuid-1", enumeration="no. 1"),
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
    assert first.title == "no. 1"


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


# ---------------------------------------------------------------------------
# Scale: the item lookup is id-scoped and batched, not a full-namespace load
# ---------------------------------------------------------------------------
def _one_item_instance(instance_id: str) -> FolioEnrichedInstance:
    return FolioEnrichedInstance(
        instance_id=instance_id,
        items=[FolioEnrichedItem(id=f"item-{instance_id}")],
    )


def test_enrichment_fetches_only_requested_ids(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """The join reads items filtered by the ids being transformed (an `In` filter),
    not the whole namespace, and attaches only the matching content."""
    items_store = _make_items_store(
        {iid: _one_item_instance(iid) for iid in ("inst-1", "inst-2", "inst-3")}
    )

    captured_filters = []
    original = items_store.get_active_namespace_records

    def spy(*args: object, **kwargs: object) -> object:
        captured_filters.append(kwargs.get("iceberg_filter"))
        return original(*args, **kwargs)  # type: ignore[arg-type]

    monkeypatch.setattr(items_store, "get_active_namespace_records", spy)

    # adapter_store is unused by the enrichment join; pass the items store as a stand-in.
    source = AdapterStoreSource(items_store, [], items_store=items_store)
    rows = [{"id": "inst-1"}, {"id": "inst-3"}, {"id": "missing"}]
    enriched = list(source._with_enrichment(rows))

    # The store was queried once, with an `In` filter (scoped read), not unfiltered.
    assert len(captured_filters) == 1
    assert isinstance(captured_filters[0], In)

    by_id = {row["id"]: row["enrichment_content"] for row in enriched}
    assert by_id["inst-1"] is not None and by_id["inst-3"] is not None
    assert by_id["missing"] is None
    # inst-2 was never requested, so its content is not present anywhere.
    assert "inst-2" not in by_id


def test_enrichment_batches_lookups(monkeypatch: pytest.MonkeyPatch) -> None:
    """With more rows than the batch size, the lookup runs once per chunk and every
    row still gets its matching enrichment."""
    monkeypatch.setattr(adapter_store_source, "ITEM_ENRICHMENT_BATCH_SIZE", 2)
    ids = [f"inst-{i}" for i in range(5)]
    items_store = _make_items_store({iid: _one_item_instance(iid) for iid in ids})

    call_count = 0
    original = items_store.get_active_namespace_records

    def spy(*args: object, **kwargs: object) -> object:
        nonlocal call_count
        call_count += 1
        return original(*args, **kwargs)  # type: ignore[arg-type]

    monkeypatch.setattr(items_store, "get_active_namespace_records", spy)

    source = AdapterStoreSource(items_store, [], items_store=items_store)
    enriched = list(source._with_enrichment([{"id": iid} for iid in ids]))

    assert len(enriched) == 5
    assert all(row["enrichment_content"] is not None for row in enriched)
    assert call_count == 3  # 5 rows, batch size 2 -> 3 chunks
