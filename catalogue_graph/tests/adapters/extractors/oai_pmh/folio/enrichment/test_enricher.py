from pyiceberg.table import Table as IcebergTable

from adapters.extractors.oai_pmh.folio.enrichment.enricher import FolioItemEnricher
from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedInstance,
    FolioEnrichedItem,
)
from adapters.utils.adapter_store import AdapterStore

ITEMS_NAMESPACE = "folio-items"


class FakeInventoryClient:
    """Returns canned enrichment responses keyed by instance id."""

    def __init__(self, responses: dict[str, FolioEnrichedInstance]) -> None:
        self.responses = responses
        self.calls: list[list[str]] = []

    def enriched_instances(
        self, instance_ids: list[str]
    ) -> list[FolioEnrichedInstance]:
        self.calls.append(list(instance_ids))
        return [self.responses[i] for i in instance_ids if i in self.responses]


def _instance(instance_id: str, item_ids: list[str]) -> FolioEnrichedInstance:
    return FolioEnrichedInstance(
        instance_id=instance_id,
        items=[FolioEnrichedItem(id=i) for i in item_ids],
    )


def _store(table: IcebergTable) -> AdapterStore:
    return AdapterStore(table, namespace=ITEMS_NAMESPACE)


def test_enrich_writes_item_rows(temporary_table: IcebergTable) -> None:
    client = FakeInventoryClient(
        {
            "inst-1": _instance("inst-1", ["item-a", "item-b"]),
            "inst-2": _instance("inst-2", ["item-c"]),
        }
    )
    store = _store(temporary_table)
    enricher = FolioItemEnricher(client, store)  # type: ignore[arg-type]

    update = enricher.enrich(["inst-1", "inst-2"])

    assert update is not None
    assert sorted(update.inserted_record_ids) == ["inst-1", "inst-2"]

    rows = {r["id"]: r for r in store.get_active_namespace_records().to_pylist()}
    assert set(rows) == {"inst-1", "inst-2"}

    stored = FolioEnrichedInstance.from_store_content(rows["inst-1"]["content"])
    assert [i.id for i in stored.items] == ["item-a", "item-b"]
    assert rows["inst-1"]["changeset"] == update.changeset_id


def test_enrich_is_idempotent_when_unchanged(temporary_table: IcebergTable) -> None:
    client = FakeInventoryClient({"inst-1": _instance("inst-1", ["item-a"])})
    store = _store(temporary_table)
    enricher = FolioItemEnricher(client, store)  # type: ignore[arg-type]

    first = enricher.enrich(["inst-1"])
    assert first is not None

    # Re-running with identical content produces no write.
    second = enricher.enrich(["inst-1"])
    assert second is None


def test_enrich_updates_changed_items(temporary_table: IcebergTable) -> None:
    client = FakeInventoryClient({"inst-1": _instance("inst-1", ["item-a"])})
    store = _store(temporary_table)
    enricher = FolioItemEnricher(client, store)  # type: ignore[arg-type]

    enricher.enrich(["inst-1"])

    # A new item appears on the instance.
    client.responses["inst-1"] = _instance("inst-1", ["item-a", "item-b"])
    update = enricher.enrich(["inst-1"])

    assert update is not None
    assert update.updated_record_ids == ["inst-1"]

    rows = {r["id"]: r for r in store.get_active_namespace_records().to_pylist()}
    stored = FolioEnrichedInstance.from_store_content(rows["inst-1"]["content"])
    assert [i.id for i in stored.items] == ["item-a", "item-b"]


def test_enrich_empty_input_is_noop(temporary_table: IcebergTable) -> None:
    client = FakeInventoryClient({})
    store = _store(temporary_table)
    enricher = FolioItemEnricher(client, store)  # type: ignore[arg-type]

    assert enricher.enrich([]) is None
    assert client.calls == []


def test_enrich_keys_by_oai_id_but_queries_bare_uuid(
    temporary_table: IcebergTable,
) -> None:
    """The bib store id is an OAI identifier; the API is queried by the bare UUID and
    the stored row is re-keyed back to the OAI id so the transform join matches."""
    uuid = "3144420c-9a8d-5738-a0a4-7cbbbbf0dc14"
    store_id = f"oai:edge-wellcome.folio.ebsco.com:fs00001190/{uuid}"
    # The API returns results keyed by the bare instance UUID.
    client = FakeInventoryClient({uuid: _instance(uuid, ["item-a"])})
    store = _store(temporary_table)
    enricher = FolioItemEnricher(client, store)  # type: ignore[arg-type]

    update = enricher.enrich([store_id])

    assert update is not None
    # Queried with the bare UUID...
    assert client.calls == [[uuid]]
    # ...but stored under the full OAI id (so it joins onto the bib row).
    rows = {r["id"]: r for r in store.get_active_namespace_records().to_pylist()}
    assert set(rows) == {store_id}
    stored = FolioEnrichedInstance.from_store_content(rows[store_id]["content"])
    assert [i.id for i in stored.items] == ["item-a"]
