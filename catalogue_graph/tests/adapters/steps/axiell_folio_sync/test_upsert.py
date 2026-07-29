from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from adapters.steps.axiell_folio_sync.mapping import (
    Holdings,
    IdRef,
    Instance,
    Item,
    MappedPayloads,
    PayloadMeta,
)
from adapters.steps.axiell_folio_sync.upsert import upsert_from_payloads

MAPPED = MappedPayloads(
    instance=Instance(hrid="AxC-instance-1", title="t", instanceTypeId="it"),
    holdings=Holdings(
        hrid="AxC-holding-1",
        sourceId="src",
        permanentLocationId="loc",
    ),
    item=Item(
        hrid="AxC-item-1",
        materialType=IdRef(id="mat"),
        permanentLoanType=IdRef(id="loan"),
        permanentLocation=IdRef(id="loc"),
    ),
    meta=PayloadMeta(
        source_id="src-1",
        instance_hrid="AxC-instance-1",
        holdings_hrid="AxC-holding-1",
        item_hrid="AxC-item-1",
        mapping_version="2.1.0",
        deleted=False,
    ),
)


def test_rolls_back_created_records_when_item_create_fails() -> None:
    deleted_paths: list[str] = []

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/inventory/instances":
                return {"instances": []}
            if path == "/holdings-storage/holdings":
                return {"holdingsRecords": []}
            if path == "/inventory/items":
                return {"items": []}
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            if path == "/inventory/instances":
                return {"id": "inst-1"}
            if path == "/holdings-storage/holdings":
                return {"id": "hold-1"}
            if path == "/inventory/items":
                raise RuntimeError("item write failed")
            return {}

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            return {}

        def delete(self, path: str) -> dict[str, Any]:
            deleted_paths.append(path)
            return {}

    result = upsert_from_payloads(
        MAPPED,
        FakeInventory(),
        dry_run=False,
    )

    assert result.errors
    assert "/holdings-storage/holdings/hold-1" in deleted_paths
    assert "/inventory/instances/inst-1" in deleted_paths


def test_deleted_record_suppression_does_not_trigger_rollbacks_on_success() -> None:
    deleted_paths: list[str] = []

    mapped = MAPPED.model_copy(
        update={"meta": MAPPED.meta.model_copy(update={"deleted": True})}
    )

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/inventory/instances":
                return {"instances": [{"id": "inst-1"}]}
            if path == "/holdings-storage/holdings":
                return {"holdingsRecords": [{"id": "hold-1"}]}
            if path == "/inventory/items":
                return {"items": [{"id": "item-1"}]}
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            return {}

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            return {}

        def delete(self, path: str) -> dict[str, Any]:
            deleted_paths.append(path)
            return {}

    result = upsert_from_payloads(
        mapped,
        FakeInventory(),
        dry_run=False,
    )

    assert not result.errors
    assert deleted_paths == []


def test_lookup_failure_errors_rather_than_creating_a_duplicate() -> None:
    # A transient FOLIO outage makes the instance *lookup* raise. This must not be
    # read as "record absent" and fall through to a POST — that would create a
    # duplicate (or 422) instead of updating the existing record. It surfaces as an
    # error and writes nothing.
    posted_paths: list[str] = []

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            raise RuntimeError("folio down")

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            posted_paths.append(path)
            return {"id": "should-not-happen"}

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            return {}

        def delete(self, path: str) -> dict[str, Any]:
            return {}

    result = upsert_from_payloads(
        MAPPED,
        FakeInventory(),
        dry_run=False,
    )

    assert result.errors
    assert result.errors[0].type == "api"
    assert posted_paths == []
    assert result.instance.action is None
