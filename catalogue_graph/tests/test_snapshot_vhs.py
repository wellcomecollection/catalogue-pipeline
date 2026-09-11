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

    assert row == IndexRow(
        id="rec001",
        version=7,
        bucket="vhs-bucket",
        key="rec001/7/abc.json",
        deleted=False,
    )


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

    rows = scan_index(resource, "vhs-calm-adapter")

    assert sorted(row.id for row in rows) == [f"rec{n:03}" for n in range(5)]


def test_scan_index_refuses_an_empty_table() -> None:
    with pytest.raises(SnapshotError, match="0 rows"):
        scan_index(FakeDynamoResource([]), "vhs-calm-adapter")


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


def test_write_snapshot_rejects_a_body_that_is_not_json(tmp_path: Path) -> None:
    rows, s3_client = build_store(["rec001"])
    s3_client.objects[rows[0].key] = "<html>not json</html>"

    with pytest.raises(SnapshotError, match="is not JSON"):
        write_snapshot(s3_client, CALM, rows, str(tmp_path / "calm.parquet"))


def test_write_snapshot_rejects_a_body_whose_id_disagrees(tmp_path: Path) -> None:
    rows, s3_client = build_store(["rec001"])
    s3_client.objects[rows[0].key] = calm_body("a-different-record")

    with pytest.raises(SnapshotError, match="index and the body disagree"):
        write_snapshot(s3_client, CALM, rows, str(tmp_path / "calm.parquet"))


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

    with pytest.raises(SnapshotError, match="Could not read"):
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
