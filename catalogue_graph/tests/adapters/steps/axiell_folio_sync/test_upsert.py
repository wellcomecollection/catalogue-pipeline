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


def test_update_preserves_foreign_admin_notes_and_replaces_the_axiell_one() -> None:
    """The sync owns one administrative note; a cataloguer's notes must survive.

    administrativeNotes is a flat list of strings, so the payload-wins merge would
    otherwise replace the whole list with the single note the mapping builds.
    """
    put_bodies: list[dict[str, Any]] = []

    existing_item = {
        "id": "item-1",
        "hrid": "AxC-item-1",
        "administrativeNotes": [
            "Checked by conservation 2026-01-04",
            "Axiell Current Location: OLD/LOCATION/1",
            "  Do not reshelve  ",
        ],
    }

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/inventory/instances":
                return {"instances": [{"id": "inst-1", "hrid": "AxC-instance-1"}]}
            if path == "/holdings-storage/holdings":
                return {"holdingsRecords": [{"id": "hold-1", "hrid": "AxC-holding-1"}]}
            if path == "/inventory/items":
                return {"items": [existing_item]}
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("nothing should be created in this test")

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            put_bodies.append(dict(payload))
            return {}

        def delete(self, path: str) -> dict[str, Any]:
            return {}

    mapped = MAPPED.model_copy(
        update={
            "item": MAPPED.item.model_copy(
                update={
                    "administrativeNotes": ["Axiell Current Location: NEW/LOCATION/2"]
                }
            )
        }
    )

    result = upsert_from_payloads(mapped, FakeInventory(), dry_run=False)

    assert result.errors == []
    assert result.item is not None
    assert result.item.action == "update"

    item_put = next(body for body in put_bodies if body["hrid"] == "AxC-item-1")
    assert item_put["administrativeNotes"] == [
        "Checked by conservation 2026-01-04",
        "  Do not reshelve  ",
        "Axiell Current Location: NEW/LOCATION/2",
    ]


def test_update_does_not_touch_admin_notes_the_payload_does_not_set() -> None:
    """Instances and holdings carry no administrativeNotes in the mapping, so an
    update must leave any the record already has exactly as they were."""
    put_bodies: list[dict[str, Any]] = []

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/inventory/instances":
                return {
                    "instances": [
                        {
                            "id": "inst-1",
                            "hrid": "AxC-instance-1",
                            "administrativeNotes": ["Merged from a duplicate record"],
                        }
                    ]
                }
            if path == "/holdings-storage/holdings":
                return {"holdingsRecords": [{"id": "hold-1", "hrid": "AxC-holding-1"}]}
            if path == "/inventory/items":
                return {"items": [{"id": "item-1", "hrid": "AxC-item-1"}]}
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("nothing should be created in this test")

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            put_bodies.append(dict(payload))
            return {}

        def delete(self, path: str) -> dict[str, Any]:
            return {}

    upsert_from_payloads(MAPPED, FakeInventory(), dry_run=False)

    instance_put = next(body for body in put_bodies if body["hrid"] == "AxC-instance-1")
    assert instance_put["administrativeNotes"] == ["Merged from a duplicate record"]


def test_item_status_is_sent_on_create() -> None:
    """status is required on item create, so the payload default has to go out."""
    post_bodies: list[dict[str, Any]] = []

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            return {"instances": [], "holdingsRecords": [], "items": []}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            post_bodies.append(dict(payload))
            return {"id": f"new-{len(post_bodies)}"}

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("nothing should be updated in this test")

        def delete(self, path: str) -> dict[str, Any]:
            return {}

    upsert_from_payloads(MAPPED, FakeInventory(), dry_run=False)

    item_post = next(body for body in post_bodies if body["hrid"] == "AxC-item-1")
    assert item_post["status"] == {"name": "Available"}


def test_update_leaves_the_folio_item_status_alone() -> None:
    """mod-circulation owns item.status once the item exists: an update must not
    reset a checked-out (or missing, or withdrawn) item to Available."""
    put_bodies: list[dict[str, Any]] = []
    circulation_status = {
        "name": "Checked out",
        "date": "2026-09-30T09:00:00.000+00:00",
    }

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/inventory/instances":
                return {"instances": [{"id": "inst-1", "hrid": "AxC-instance-1"}]}
            if path == "/holdings-storage/holdings":
                return {"holdingsRecords": [{"id": "hold-1", "hrid": "AxC-holding-1"}]}
            if path == "/inventory/items":
                return {
                    "items": [
                        {
                            "id": "item-1",
                            "hrid": "AxC-item-1",
                            "status": circulation_status,
                        }
                    ]
                }
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("nothing should be created in this test")

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            put_bodies.append(dict(payload))
            return {}

        def delete(self, path: str) -> dict[str, Any]:
            return {}

    upsert_from_payloads(MAPPED, FakeInventory(), dry_run=False)

    item_put = next(body for body in put_bodies if body["hrid"] == "AxC-item-1")
    assert item_put["status"] == circulation_status


def test_update_falls_back_to_our_status_when_folio_sends_none() -> None:
    """status is required on update as well as create, so an existing record that
    carries none must not produce a PUT with the field missing, which FOLIO would
    reject with a 422. The hrid lookup returns whole records, so this path is
    belt and braces."""
    put_bodies: list[dict[str, Any]] = []

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/inventory/instances":
                return {"instances": [{"id": "inst-1", "hrid": "AxC-instance-1"}]}
            if path == "/holdings-storage/holdings":
                return {"holdingsRecords": [{"id": "hold-1", "hrid": "AxC-holding-1"}]}
            if path == "/inventory/items":
                # No "status" key, as an abridged projection would give, or a
                # lookup swapped for a lighter endpoint.
                return {"items": [{"id": "item-1", "hrid": "AxC-item-1"}]}
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("nothing should be created in this test")

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            put_bodies.append(dict(payload))
            return {}

        def delete(self, path: str) -> dict[str, Any]:
            return {}

    upsert_from_payloads(MAPPED, FakeInventory(), dry_run=False)

    item_put = next(body for body in put_bodies if body["hrid"] == "AxC-item-1")
    assert item_put["status"] == {"name": "Available"}


def _put_bodies_for(existing_notes: list[str], incoming_note: str) -> list[str]:
    """Run one item update and return the administrativeNotes that were PUT."""
    captured: list[dict[str, Any]] = []

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/inventory/instances":
                return {"instances": [{"id": "inst-1", "hrid": "AxC-instance-1"}]}
            if path == "/holdings-storage/holdings":
                return {"holdingsRecords": [{"id": "hold-1", "hrid": "AxC-holding-1"}]}
            if path == "/inventory/items":
                return {
                    "items": [
                        {
                            "id": "item-1",
                            "hrid": "AxC-item-1",
                            "administrativeNotes": existing_notes,
                        }
                    ]
                }
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("nothing should be created in this test")

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            captured.append(dict(payload))
            return {}

        def delete(self, path: str) -> dict[str, Any]:
            return {}

    mapped = MAPPED.model_copy(
        update={
            "item": MAPPED.item.model_copy(
                update={"administrativeNotes": [incoming_note]}
            )
        }
    )
    upsert_from_payloads(mapped, FakeInventory(), dry_run=False)
    item_put = next(body for body in captured if body["hrid"] == "AxC-item-1")
    return list(item_put["administrativeNotes"])


NEW_NOTE = "Axiell Current Location: NEW"


def test_a_note_under_the_current_label_is_replaced() -> None:
    notes = _put_bodies_for(["Axiell Current Location: OLD"], NEW_NOTE)
    assert notes == [NEW_NOTE]


def test_the_label_is_matched_case_insensitively() -> None:
    """A capitalisation "correction" in the FOLIO UI must not orphan the note."""
    notes = _put_bodies_for(["axiell current location: OLD"], NEW_NOTE)
    assert notes == [NEW_NOTE]


def test_a_note_without_our_label_is_left_alone() -> None:
    """Without the label it is indistinguishable from a cataloguer's note."""
    notes = _put_bodies_for(["Shelf location per Axiell: OLD"], NEW_NOTE)
    assert notes == ["Shelf location per Axiell: OLD", NEW_NOTE]


def _result_for(existing_notes: list[str], dry_run: bool = False) -> Any:
    """Run one item update against an existing item and return the UpsertResult."""

    class FakeInventory:
        def get(
            self, path: str, params: Mapping[str, Any] | None = None
        ) -> dict[str, Any]:
            if path == "/inventory/instances":
                return {"instances": [{"id": "inst-1", "hrid": "AxC-instance-1"}]}
            if path == "/holdings-storage/holdings":
                return {"holdingsRecords": [{"id": "hold-1", "hrid": "AxC-holding-1"}]}
            if path == "/inventory/items":
                return {
                    "items": [
                        {
                            "id": "item-1",
                            "hrid": "AxC-item-1",
                            "administrativeNotes": existing_notes,
                        }
                    ]
                }
            return {}

        def post(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            raise AssertionError("nothing should be created in this test")

        def put(self, path: str, payload: Mapping[str, Any]) -> dict[str, Any]:
            return {}

        def delete(self, path: str) -> dict[str, Any]:
            return {}

    mapped = MAPPED.model_copy(
        update={
            "item": MAPPED.item.model_copy(update={"administrativeNotes": [NEW_NOTE]})
        }
    )
    return upsert_from_payloads(mapped, FakeInventory(), dry_run=dry_run)


def test_a_note_the_sync_cannot_claim_is_reported() -> None:
    """The duplicate cannot be prevented, but it must not be silent."""
    result = _result_for(["Axiell Current333565 Location: OLD"])
    assert result.stray_location_notes == ["Axiell Current333565 Location: OLD"]


def test_claimable_and_unrelated_notes_are_not_reported() -> None:
    result = _result_for(
        [
            "Axiell Current Location: OLD",  # ours, claimable
            "Conservation check 2026-09-22",  # nothing to do with us
        ]
    )
    assert result.stray_location_notes == []


def test_strays_are_reported_on_a_dry_run_too() -> None:
    """A dry run is used to validate; it must surface the same warning."""
    result = _result_for(["Axiell Current333565 Location: OLD"], dry_run=True)
    assert result.stray_location_notes == ["Axiell Current333565 Location: OLD"]
