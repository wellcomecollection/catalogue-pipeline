import decimal
import io
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pyarrow.parquet as pq
import pytest

from scripts.snapshot_vhs import (
    VHS_STORES,
    IndexRow,
    SnapshotError,
    VHSStoreConfig,
    _parse_index_row,
    scan_index,
    snapshot_vhs,
    upload_snapshot,
    verify_snapshot,
    write_snapshot,
)

CALM = VHS_STORES["calm"]
LAST_MODIFIED = datetime(2026, 9, 10, 12, 0, tzinfo=UTC)


def calm_body(record_id: str) -> str:
    return json.dumps(
        {
            "id": record_id,
            "data": {"RefNo": ["PP/ABC/1"]},
            "retrievedAt": "2026-09-01T00:00:00Z",
            "published": True,
        }
    )


def index_item(record_id: str, version: int = 1, **extra: Any) -> dict[str, Any]:
    return {
        "id": record_id,
        "version": version,
        "payload": {
            "bucket": "vhs-bucket",
            "key": f"{record_id}/{version}/abc.json",
        },
        **extra,
    }


class FakeS3Client:
    """Serves object bodies by key, and records uploads."""

    def __init__(self, objects: dict[str, str]) -> None:
        self.objects = objects
        self.uploads: list[tuple[str, str, str]] = []

    def get_object(self, Bucket: str, Key: str) -> dict[str, Any]:  # noqa: N803
        if Key not in self.objects:
            raise KeyError(f"No such key: {Key}")
        return {
            "Body": io.BytesIO(self.objects[Key].encode("utf8")),
            "LastModified": LAST_MODIFIED,
        }

    def upload_file(self, Filename: str, Bucket: str, Key: str) -> None:  # noqa: N803
        self.uploads.append((Filename, Bucket, Key))


class FakePaginator:
    def __init__(self, items_by_segment: dict[int, list[dict]]) -> None:
        self.items_by_segment = items_by_segment

    def paginate(
        self, TableName: str, Segment: int, TotalSegments: int, **kwargs: Any
    ) -> list[dict]:  # noqa: N803
        return [{"Items": self.items_by_segment.get(Segment, [])}]


class FakeDynamoResource:
    def __init__(self, items: list[dict]) -> None:
        # Everything lands in segment 0; the split is DynamoDB's job, not ours.
        paginator = FakePaginator({0: items})
        client = type("Client", (), {"get_paginator": lambda self, name: paginator})()
        self.meta = type("Meta", (), {"client": client})()


def build_store(record_ids: list[str]) -> tuple[list[IndexRow], FakeS3Client]:
    rows = [_parse_index_row(index_item(record_id)) for record_id in record_ids]
    objects = {row.key: calm_body(row.id) for row in rows}
    return rows, FakeS3Client(objects)


def test_parse_index_row_reads_the_payload_form() -> None:
    row = _parse_index_row(index_item("rec001", version=7))

    assert (row.id, row.version, row.bucket, row.key, row.deleted) == (
        "rec001",
        7,
        "vhs-bucket",
        "rec001/7/abc.json",
        False,
    )
    assert json.loads(row.raw)["payload"]["bucket"] == "vhs-bucket"


def test_parse_index_row_reads_the_location_form() -> None:
    item = {
        "id": "rec001",
        "version": 2,
        "location": {"bucket": "vhs-bucket", "key": "rec001/2/abc.json"},
    }

    assert _parse_index_row(item).key == "rec001/2/abc.json"


def test_parse_index_row_carries_the_deletion_marker() -> None:
    assert _parse_index_row(index_item("rec001", isDeleted=True)).deleted is True


def test_parse_index_row_keeps_the_key_when_the_version_has_moved_on() -> None:
    """The deletion checker bumps the version without writing a new object, so
    the key must come from the row rather than being rebuilt from the version."""
    item = index_item("rec001", version=4, isDeleted=True)
    item["version"] = 5

    row = _parse_index_row(item)

    assert row.version == 5
    assert row.key == "rec001/4/abc.json"


def test_parse_index_row_rejects_a_row_with_no_location() -> None:
    with pytest.raises(SnapshotError, match="no payload or location"):
        _parse_index_row({"id": "rec001", "version": 1})


def test_scan_index_reads_every_row() -> None:
    resource = FakeDynamoResource([index_item(f"rec{n:03}") for n in range(5)])

    rows = scan_index(resource, CALM)

    assert sorted(row.id for row in rows) == [f"rec{n:03}" for n in range(5)]


def test_scan_index_refuses_an_empty_table() -> None:
    with pytest.raises(SnapshotError, match="0 rows"):
        scan_index(FakeDynamoResource([]), CALM)


def test_write_snapshot_round_trips_every_record(tmp_path: Path) -> None:
    rows, s3_client = build_store(["rec001", "rec002", "rec003"])
    output_path = str(tmp_path / "calm.parquet")

    written = write_snapshot(s3_client, CALM, rows, output_path)

    assert written == 3
    table = pq.read_table(output_path)
    assert table.num_rows == 3

    by_id = {str(row["id"]): row for row in table.to_pylist()}
    assert sorted(by_id) == ["rec001", "rec002", "rec003"]

    row = by_id["rec002"]
    assert row["namespace"] == "calm"
    assert row["deleted"] is False
    assert row["version"] == 1
    assert row["s3_key"] == "rec002/1/abc.json"
    assert json.loads(str(row["content"]))["id"] == "rec002"


def test_write_snapshot_preserves_the_whole_index_row(tmp_path: Path) -> None:
    """Miro keeps isClearedForCatalogueAPI, events and overrides on the row and
    nowhere else, so naming only the fields we parse would drop them."""
    item = index_item("rec001", isClearedForCatalogueAPI=False, overrides={"a": "b"})
    rows = [_parse_index_row(item)]
    s3_client = FakeS3Client({rows[0].key: calm_body("rec001")})
    output_path = str(tmp_path / "miro.parquet")

    write_snapshot(s3_client, CALM, rows, output_path)

    stored = json.loads(str(pq.read_table(output_path).to_pylist()[0]["index_row"]))
    assert stored["isClearedForCatalogueAPI"] is False
    assert stored["overrides"] == {"a": "b"}


def test_write_snapshot_serialises_the_types_dynamodb_returns(tmp_path: Path) -> None:
    item = index_item("rec001")
    item["version"] = decimal.Decimal(3)
    item["tags"] = {"b", "a"}
    rows = [_parse_index_row(item)]
    s3_client = FakeS3Client({rows[0].key: calm_body("rec001")})
    output_path = str(tmp_path / "calm.parquet")

    write_snapshot(s3_client, CALM, rows, output_path)

    stored = json.loads(str(pq.read_table(output_path).to_pylist()[0]["index_row"]))
    assert stored["version"] == 3
    assert stored["tags"] == ["a", "b"]


@pytest.mark.parametrize(
    ("body", "reason"),
    [
        ("<html>not json</html>", "a body that is not JSON"),
        (calm_body("a-different-record"), "a body whose id disagrees"),
    ],
)
def test_write_snapshot_fails_the_run_on(
    tmp_path: Path, body: str, reason: str
) -> None:
    rows, s3_client = build_store(["rec001"])
    s3_client.objects[rows[0].key] = body

    with pytest.raises(SnapshotError, match="over the 0 allowed"):
        write_snapshot(s3_client, CALM, rows, str(tmp_path / "calm.parquet"))

    assert list(tmp_path.iterdir()) == [], reason


def test_write_snapshot_reports_every_bad_record_in_one_run(tmp_path: Path) -> None:
    """One bad object must not cost the run, or a store with three of them
    takes three full passes to discover them all."""
    rows, s3_client = build_store(["rec001", "rec002", "rec003"])
    del s3_client.objects[rows[0].key]
    s3_client.objects[rows[2].key] = "not json"

    with pytest.raises(SnapshotError, match=r"2 record\(s\) had no readable body"):
        write_snapshot(s3_client, CALM, rows, str(tmp_path / "calm.parquet"))


def test_write_snapshot_can_be_allowed_to_finish_with_null_content(
    tmp_path: Path,
) -> None:
    rows, s3_client = build_store(["rec001", "rec002"])
    del s3_client.objects[rows[1].key]
    output_path = str(tmp_path / "calm.parquet")

    written = write_snapshot(s3_client, CALM, rows, output_path, allow_unreadable=1)

    assert written == 2
    by_id = {str(r["id"]): r for r in pq.read_table(output_path).to_pylist()}
    assert by_id["rec002"]["content"] is None
    # The index fields survive, so the record is still accounted for.
    assert by_id["rec002"]["s3_key"] == "rec002/1/abc.json"


def test_write_snapshot_skips_the_id_check_for_stores_without_one(
    tmp_path: Path,
) -> None:
    rows, s3_client = build_store(["rec001"])
    s3_client.objects[rows[0].key] = json.dumps({"anything": True})
    config = VHSStoreConfig(table_name="vhs-other", namespace="other")

    assert write_snapshot(s3_client, config, rows, str(tmp_path / "other.parquet")) == 1


def test_write_snapshot_leaves_no_partial_file_behind(tmp_path: Path) -> None:
    rows, s3_client = build_store(["rec001", "rec002"])
    del s3_client.objects[rows[1].key]
    output_path = str(tmp_path / "calm.parquet")

    with pytest.raises(SnapshotError, match="no readable body"):
        write_snapshot(s3_client, CALM, rows, output_path)

    assert list(tmp_path.iterdir()) == []


def test_verify_snapshot_accepts_a_complete_file(tmp_path: Path) -> None:
    rows, s3_client = build_store(["rec001", "rec002"])
    output_path = str(tmp_path / "calm.parquet")
    write_snapshot(s3_client, CALM, rows, output_path)

    verify_snapshot(output_path, rows)


def test_verify_snapshot_catches_a_record_that_never_made_it(tmp_path: Path) -> None:
    rows, s3_client = build_store(["rec001", "rec002"])
    output_path = str(tmp_path / "calm.parquet")
    write_snapshot(s3_client, CALM, rows[:1], output_path)

    with pytest.raises(SnapshotError, match="1 rows but the index had 2"):
        verify_snapshot(output_path, rows)


def test_upload_snapshot_builds_the_key_from_the_prefix(tmp_path: Path) -> None:
    output_path = tmp_path / "calm.parquet"
    output_path.write_text("")
    s3_client = FakeS3Client({})

    uri = upload_snapshot(s3_client, str(output_path), "s3://infra/vhs_snapshots/calm/")

    assert uri == "s3://infra/vhs_snapshots/calm/calm.parquet"
    assert s3_client.uploads == [
        (str(output_path), "infra", "vhs_snapshots/calm/calm.parquet")
    ]


def test_upload_snapshot_rejects_a_non_s3_destination(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="must be an s3:// URI"):
        upload_snapshot(FakeS3Client({}), str(tmp_path / "calm.parquet"), "/local/path")


def test_snapshot_vhs_scans_fetches_and_verifies(tmp_path: Path) -> None:
    record_ids = ["rec001", "rec002"]
    rows, s3_client = build_store(record_ids)

    class FakeSession:
        def resource(self, name: str) -> FakeDynamoResource:
            return FakeDynamoResource([index_item(rid) for rid in record_ids])

        def client(self, name: str, **kwargs: Any) -> FakeS3Client:
            return s3_client

    output_path = str(tmp_path / "calm.parquet")

    written = snapshot_vhs("calm", output_path, session=FakeSession())

    assert written == 2
    assert pq.read_table(output_path).num_rows == 2


class FakeMultiTableDynamoResource:
    """Serves different items per table, so the deleted companion is visible."""

    def __init__(self, items_by_table: dict[str, list[dict]]) -> None:
        outer = self

        class Client:
            def get_paginator(self, name: str) -> Any:
                class Paginator:
                    def paginate(
                        self,
                        TableName: str,  # noqa: N803
                        Segment: int,  # noqa: N803
                        TotalSegments: int,  # noqa: N803
                        **kwargs: Any,
                    ) -> list[dict]:
                        if Segment != 0:
                            return [{"Items": []}]
                        return [{"Items": outer.items_by_table.get(TableName, [])}]

                return Paginator()

        self.items_by_table = items_by_table
        self.meta = type("Meta", (), {"client": Client()})()


def test_scan_index_includes_the_deleted_companion_table() -> None:
    """Sierra moved its pre-2018 deletions into a separate table rather than
    marking them in place, so reading only the main table loses 557,348 of them."""
    resource = FakeMultiTableDynamoResource(
        {
            "vhs-sierra-sierra-adapter-20200604": [index_item("live001")],
            "vhs-sierra-sierra-adapter-20200604-deleted": [index_item("gone001")],
        }
    )

    rows = scan_index(resource, VHS_STORES["sierra"])

    by_id = {row.id: row for row in rows}
    assert by_id["live001"].deleted is False
    assert by_id["gone001"].deleted is True


def test_scan_index_leaves_a_store_without_a_companion_table_alone() -> None:
    resource = FakeMultiTableDynamoResource(
        {"vhs-calm-adapter": [index_item("rec001")]}
    )

    assert len(scan_index(resource, CALM)) == 1


@pytest.mark.parametrize("limit", [0, -1])
def test_snapshot_vhs_rejects_a_limit_below_one(tmp_path: Path, limit: int) -> None:
    """--limit 0 would write a valid empty parquet over the output path, and a
    negative one a file silently short of the store."""
    with pytest.raises(ValueError, match="must be at least 1"):
        snapshot_vhs("calm", str(tmp_path / "calm.parquet"), limit=limit)


def test_snapshot_vhs_rejects_uploading_a_limited_run(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="cannot be uploaded"):
        snapshot_vhs(
            "calm", str(tmp_path / "calm.parquet"), limit=5, upload_to="s3://infra/x/"
        )


def test_snapshot_schema_still_matches_the_adapter_store() -> None:
    """The first six fields are deliberately the adapter store's, so a snapshot
    loads through its path. Nothing else enforces that if schemata.py moves."""
    from adapters.utils.schemata import ADAPTER_STORE_ICEBERG_SCHEMA
    from scripts.snapshot_vhs import VHS_SNAPSHOT_ICEBERG_SCHEMA

    adapter_fields = ADAPTER_STORE_ICEBERG_SCHEMA.fields
    snapshot_fields = VHS_SNAPSHOT_ICEBERG_SCHEMA.fields[: len(adapter_fields)]

    assert [(f.name, f.field_type, f.required) for f in snapshot_fields] == [
        (f.name, f.field_type, f.required) for f in adapter_fields
    ]
