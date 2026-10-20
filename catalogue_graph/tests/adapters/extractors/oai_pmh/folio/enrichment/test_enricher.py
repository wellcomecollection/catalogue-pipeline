import json
from typing import Any

import httpx
import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.extractors.oai_pmh.folio.enrichment.enricher import (
    REJECTED_COUNT_TO_FAIL,
    FolioInventoryEnricher,
)
from adapters.extractors.oai_pmh.folio.enrichment.inventory_client import (
    FolioInventoryClient,
)
from adapters.extractors.oai_pmh.folio.enrichment.models import (
    FolioEnrichedInstance,
    InventoryFetch,
    parse_inventory_records,
)
from adapters.utils.adapter_store import AdapterStore
from tests.adapters.transformers.folio.helpers import (
    fixture_item_ids,
    fixture_record,
    folio_record,
)

INVENTORY_NAMESPACE = "folio-inventory"


class FakeInventoryClient:
    """Returns canned enrichment responses keyed by instance id."""

    def __init__(self, responses: dict[str, dict[str, Any]]) -> None:
        self.responses = responses
        self.calls: list[list[str]] = []

    def items_and_holdings(self, instance_ids: list[str]) -> InventoryFetch:
        self.calls.append(list(instance_ids))
        return parse_inventory_records(
            [self.responses[i] for i in instance_ids if i in self.responses]
        )


def _store(table: IcebergTable) -> AdapterStore:
    return AdapterStore(table, namespace=INVENTORY_NAMESPACE)


def test_enrich_writes_item_rows(temporary_table: IcebergTable) -> None:
    client = FakeInventoryClient(
        {
            "inst-1": fixture_record("inst-1"),
            "inst-2": fixture_record("inst-2"),
        }
    )
    store = _store(temporary_table)
    enricher = FolioInventoryEnricher(client, store)  # type: ignore[arg-type]

    update, rejected = enricher.enrich(["inst-1", "inst-2"])
    assert rejected == []

    assert update is not None
    assert sorted(update.inserted_record_ids) == ["inst-1", "inst-2"]

    rows = {r["id"]: r for r in store.get_active_namespace_records().to_pylist()}
    assert set(rows) == {"inst-1", "inst-2"}

    stored = FolioEnrichedInstance.from_store_content(rows["inst-1"]["content"])
    assert [i.id for i in stored.items] == fixture_item_ids()
    assert rows["inst-1"]["changeset"] == update.changeset_id


def test_enrich_is_idempotent_when_unchanged(temporary_table: IcebergTable) -> None:
    client = FakeInventoryClient({"inst-1": fixture_record("inst-1")})
    store = _store(temporary_table)
    enricher = FolioInventoryEnricher(client, store)  # type: ignore[arg-type]

    first, _ = enricher.enrich(["inst-1"])
    assert first is not None

    # Re-running with identical content produces no write.
    second, _ = enricher.enrich(["inst-1"])
    assert second is None


def test_enrich_updates_changed_items(temporary_table: IcebergTable) -> None:
    before = fixture_record("inst-1")
    before["items"].pop()
    client = FakeInventoryClient({"inst-1": before})
    store = _store(temporary_table)
    enricher = FolioInventoryEnricher(client, store)  # type: ignore[arg-type]

    enricher.enrich(["inst-1"])

    # A new item appears on the instance.
    client.responses["inst-1"] = fixture_record("inst-1")
    update, _ = enricher.enrich(["inst-1"])

    assert update is not None
    assert update.updated_record_ids == ["inst-1"]

    rows = {r["id"]: r for r in store.get_active_namespace_records().to_pylist()}
    stored = FolioEnrichedInstance.from_store_content(rows["inst-1"]["content"])
    assert [i.id for i in stored.items] == fixture_item_ids()


def test_enrich_empty_input_is_noop(temporary_table: IcebergTable) -> None:
    client = FakeInventoryClient({})
    store = _store(temporary_table)
    enricher = FolioInventoryEnricher(client, store)  # type: ignore[arg-type]

    assert enricher.enrich([]) == (None, [])
    assert client.calls == []


def test_enrich_keys_by_oai_id_but_queries_bare_uuid(
    temporary_table: IcebergTable,
) -> None:
    """The bib store id is an OAI identifier; the API is queried by the bare UUID and
    the stored row is re-keyed back to the OAI id so the transform join matches."""
    uuid = "3144420c-9a8d-5738-a0a4-7cbbbbf0dc14"
    store_id = f"oai:edge-wellcome.folio.ebsco.com:fs00001190/{uuid}"
    # The API returns results keyed by the bare instance UUID.
    client = FakeInventoryClient({uuid: fixture_record(uuid)})
    store = _store(temporary_table)
    enricher = FolioInventoryEnricher(client, store)  # type: ignore[arg-type]

    update, _ = enricher.enrich([store_id])

    assert update is not None
    # Queried with the bare UUID...
    assert client.calls == [[uuid]]
    # ...but stored under the full OAI id (so it joins onto the bib row).
    rows = {r["id"]: r for r in store.get_active_namespace_records().to_pylist()}
    assert set(rows) == {store_id}
    stored = FolioEnrichedInstance.from_store_content(rows[store_id]["content"])
    assert [i.id for i in stored.items] == fixture_item_ids()


def test_enrich_rejects_a_returned_id_that_matches_no_request(
    temporary_table: IcebergTable,
) -> None:
    """An instance the API returns whose id matches no requested id cannot be keyed
    onto a bib row, so it's left out rather than stored as an orphan row."""
    client = FakeInventoryClient({"inst-1": fixture_record("unexpected")})
    store = _store(temporary_table)
    enricher = FolioInventoryEnricher(client, store)  # type: ignore[arg-type]

    update, rejected = enricher.enrich(["inst-1"])

    assert update is None
    assert [(r.instance_id, r.reason) for r in rejected] == [
        ("unexpected", "instance id matched no requested id")
    ]
    assert store.get_active_namespace_records().num_rows == 0


def test_enrich_writes_the_rest_when_one_record_is_broken(
    temporary_table: IcebergTable,
) -> None:
    broken = fixture_record("inst-2")
    broken["items"][0]["holdingsRecordId"] = "not-in-this-response"
    client = FakeInventoryClient(
        {
            "inst-1": fixture_record("inst-1"),
            "inst-2": broken,
            "inst-3": fixture_record("inst-3"),
        }
    )
    store = _store(temporary_table)
    enricher = FolioInventoryEnricher(client, store)  # type: ignore[arg-type]

    update, rejected = enricher.enrich(["inst-1", "inst-2", "inst-3"])

    assert update is not None
    assert sorted(update.inserted_record_ids) == ["inst-1", "inst-3"]
    assert [r.instance_id for r in rejected] == ["inst-2"]
    assert "links to no holdings record" in rejected[0].reason


def test_enrich_fails_when_rejections_look_systemic(
    temporary_table: IcebergTable,
) -> None:
    """Many rejections at once look like a FOLIO-wide change, not a few bad records."""
    responses = {}
    for n in range(REJECTED_COUNT_TO_FAIL):
        record = fixture_record(f"inst-{n}")
        del record["holdings"][0]["id"]
        responses[f"inst-{n}"] = record
    client = FakeInventoryClient(responses)
    store = _store(temporary_table)
    enricher = FolioInventoryEnricher(client, store)  # type: ignore[arg-type]

    with pytest.raises(ValueError, match="systemic"):
        enricher.enrich(list(responses))
    assert store.get_active_namespace_records().num_rows == 0


def test_enrich_stores_the_fixture_unchanged(temporary_table: IcebergTable) -> None:
    """The fixture, served as the endpoint streams it, goes through the real client
    and is stored as it came."""
    record = fixture_record()
    # Most instances have no items, so the stream mixes both.
    empty = folio_record("empty-instance", [])
    instance_id = record["instanceId"]
    store_id = f"oai:edge-wellcome.folio.ebsco.com:fs00001190/{instance_id}"

    def handler(request: httpx.Request) -> httpx.Response:
        assert json.loads(request.content)["instanceIds"] == [
            instance_id,
            "empty-instance",
        ]
        # Concatenated objects with no array or separators, as the endpoint sends.
        return httpx.Response(200, text=json.dumps(record) + json.dumps(empty))

    client = FolioInventoryClient(
        base_url="https://inventory.example",
        client=httpx.Client(transport=httpx.MockTransport(handler)),
    )
    store = _store(temporary_table)

    update, _ = FolioInventoryEnricher(client, store).enrich(
        [store_id, "empty-instance"]
    )

    assert update is not None
    rows = {r["id"]: r for r in store.get_active_namespace_records().to_pylist()}
    assert set(rows) == {store_id, "empty-instance"}
    row = rows[store_id]
    assert json.loads(row["content"]) == record

    stored = FolioEnrichedInstance.from_store_content(row["content"])
    assert len(stored.items) == 61
    assert len(json.loads(row["content"])["holdings"]) == 27
