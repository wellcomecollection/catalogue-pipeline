from __future__ import annotations

from typing import Any

from adapters.transformers.axiell_folio_sync.upsert import upsert_from_payloads

MAPPED: dict[str, Any] = {
    "instance": {"hrid": "AxC-instance-1", "title": "t", "instanceTypeId": "it"},
    "holdings": {
        "hrid": "AxC-holding-1",
        "sourceId": "src",
        "permanentLocationId": "loc",
    },
    "item": {
        "hrid": "AxC-item-1",
        "materialType": {"id": "mat"},
        "permanentLoanType": {"id": "loan"},
        "permanentLocation": {"id": "loc"},
    },
    "meta": {
        "source_id": "src-1",
        "instance_hrid": "AxC-instance-1",
        "holdings_hrid": "AxC-holding-1",
        "item_hrid": "AxC-item-1",
        "deleted": False,
    },
}


def test_rolls_back_created_records_when_item_create_fails() -> None:
    deleted_paths: list[str] = []

    def folio_get(path: str, params: dict | None = None) -> dict:
        if path == "/inventory/instances":
            return {"instances": []}
        if path == "/holdings-storage/holdings":
            return {"holdingsRecords": []}
        if path == "/inventory/items":
            return {"items": []}
        return {}

    def folio_post(path: str, payload: dict) -> dict:
        if path == "/inventory/instances":
            return {"id": "inst-1"}
        if path == "/holdings-storage/holdings":
            return {"id": "hold-1"}
        if path == "/inventory/items":
            raise RuntimeError("item write failed")
        return {}

    def folio_put(path: str, payload: dict) -> dict:
        return {}

    def folio_delete(path: str) -> dict:
        deleted_paths.append(path)
        return {}

    result = upsert_from_payloads(
        MAPPED,
        folio_get,
        folio_post,
        folio_put,
        folio_delete,
        dry_run=False,
    )

    assert result["errors"]
    assert "/holdings-storage/holdings/hold-1" in deleted_paths
    assert "/inventory/instances/inst-1" in deleted_paths


def test_deleted_record_suppression_does_not_trigger_rollbacks_on_success() -> None:
    deleted_paths: list[str] = []

    mapped: dict[str, Any] = {
        **MAPPED,
        "meta": {**MAPPED["meta"], "deleted": True},
    }

    def folio_get(path: str, params: dict | None = None) -> dict:
        if path == "/inventory/instances":
            return {"instances": [{"id": "inst-1"}]}
        if path == "/holdings-storage/holdings":
            return {"holdingsRecords": [{"id": "hold-1"}]}
        if path == "/inventory/items":
            return {"items": [{"id": "item-1"}]}
        return {}

    def folio_post(path: str, payload: dict) -> dict:
        return {}

    def folio_put(path: str, payload: dict) -> dict:
        return {}

    def folio_delete(path: str) -> dict:
        deleted_paths.append(path)
        return {}

    result = upsert_from_payloads(
        mapped,
        folio_get,
        folio_post,
        folio_put,
        folio_delete,
        dry_run=False,
    )

    assert not result["errors"]
    assert deleted_paths == []
